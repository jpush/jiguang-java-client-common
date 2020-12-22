package cn.jiguang.common.connection;

import cn.jiguang.common.ClientConfig;
import cn.jiguang.common.resp.APIConnectionException;
import cn.jiguang.common.resp.APIRequestException;
import cn.jiguang.common.resp.ResponseWrapper;
import cn.jiguang.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.activation.MimetypesFileTypeMap;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.Iterator;
import java.util.Map;

/**
 * The implementation has no connection pool mechanism, used origin java connection.
 * <p>
 * 本实现没有连接池机制，基于 Java 原始的 HTTP 连接实现。
 * <p>
 * 遇到连接超时，会自动重连指定的次数（默认为 3）；如果是读取超时，则不会自动重连。
 * <p>
 * 可选支持 HTTP 代理，同时支持 2 种方式：1) HTTP 头上加上 Proxy-Authorization 信息；2）全局配置 Authenticator.setDefault；
 */
public class NativeHttpClient implements IHttpClient {
    private static final Logger LOG = LoggerFactory.getLogger(NativeHttpClient.class);
    private static final String KEYWORDS_CONNECT_TIMED_OUT = "connect timed out";
    private static final String KEYWORDS_READ_TIMED_OUT = "Read timed out";

    private final int _connectionTimeout;
    private final int _readTimeout;
    private final int _maxRetryTimes;
    private final String _sslVer;
    private final String _encryptType;

    private String _authCode;
    private HttpProxy _proxy;

    public NativeHttpClient(String authCode, HttpProxy proxy, ClientConfig config) {
        _maxRetryTimes = config.getMaxRetryTimes();
        _connectionTimeout = config.getConnectionTimeout();
        _readTimeout = config.getReadTimeout();
        _sslVer = config.getSSLVersion();
        _encryptType = config.getEncryptType();
        _authCode = authCode;
        _proxy = proxy;

        String message = MessageFormat.format("Created instance with "
                        + "connectionTimeout {0}, readTimeout {1}, maxRetryTimes {2}, SSL Version {3}",
                _connectionTimeout, _readTimeout, _maxRetryTimes, _sslVer);
        LOG.debug(message);

        if (null != _proxy && _proxy.isAuthenticationNeeded()) {
            Authenticator.setDefault(new SimpleProxyAuthenticator(
                    _proxy.getUsername(), _proxy.getPassword()));
        }
        initSSL(_sslVer);
    }

    public ResponseWrapper sendGet(String url)
            throws APIConnectionException, APIRequestException {
        return sendGet(url, null);
    }

    public ResponseWrapper sendGet(String url, String content)
            throws APIConnectionException, APIRequestException {
        return doRequest(url, content, RequestMethod.GET);
    }

    public ResponseWrapper sendDelete(String url)
            throws APIConnectionException, APIRequestException {
        return sendDelete(url, null);
    }

    public ResponseWrapper sendDelete(String url, String content)
            throws APIConnectionException, APIRequestException {
        return doRequest(url, content, RequestMethod.DELETE);
    }

    public ResponseWrapper sendPost(String url, String content)
            throws APIConnectionException, APIRequestException {
        return doRequest(url, content, RequestMethod.POST);
    }

    public ResponseWrapper sendPut(String url, String content)
            throws APIConnectionException, APIRequestException {
        return doRequest(url, content, RequestMethod.PUT);
    }

    public ResponseWrapper doRequest(String url, String content,
                                     RequestMethod method) throws APIConnectionException, APIRequestException {
        ResponseWrapper response = null;
        for (int retryTimes = 0; ; retryTimes++) {
            try {
                response = _doRequest(url, content, method);
                break;
            } catch (SocketTimeoutException e) {
                if (KEYWORDS_READ_TIMED_OUT.equals(e.getMessage())) {
                    // Read timed out.  For push, maybe should not re-send.
                    throw new APIConnectionException(READ_TIMED_OUT_MESSAGE, e, true);
                } else {    // connect timed out
                    if (retryTimes >= _maxRetryTimes) {
                        throw new APIConnectionException(CONNECT_TIMED_OUT_MESSAGE, e, retryTimes);
                    } else {
                        LOG.debug("connect timed out - retry again - " + (retryTimes + 1));
                    }
                }
            }
        }
        return response;
    }

    private ResponseWrapper _doRequest(String url, String content,
                                       RequestMethod method) throws APIConnectionException, APIRequestException,
            SocketTimeoutException {

        LOG.debug("Send request - " + method.toString() + " " + url);
        if (null != content) {
            LOG.debug("Request Content - " + content);
        }
        HttpURLConnection conn = null;
        OutputStream out = null;
        InputStream in = null;
        InputStreamReader reader = null;
        StringBuffer sb = new StringBuffer();
        ResponseWrapper wrapper = new ResponseWrapper();

        try {
            URL aUrl = new URL(url);

            if (null != _proxy) {
                conn = (HttpURLConnection) aUrl.openConnection(_proxy.getNetProxy());
                if (_proxy.isAuthenticationNeeded()) {
                    conn.setRequestProperty("Proxy-Authorization", _proxy.getProxyAuthorization());
                }
            } else {
                conn = (HttpURLConnection) aUrl.openConnection();
            }

            conn.setConnectTimeout(_connectionTimeout);
            conn.setReadTimeout(_readTimeout);
            conn.setUseCaches(false);
            conn.setRequestMethod(method.name());
            if (!StringUtils.isEmpty(_encryptType)) {
                conn.setRequestProperty("X-Encrypt-Type", _encryptType);
            }
            conn.setRequestProperty("User-Agent", JPUSH_USER_AGENT);
            conn.setRequestProperty("Connection", "Keep-Alive");
            conn.setRequestProperty("Accept-Charset", CHARSET);
            conn.setRequestProperty("Charset", CHARSET);
            conn.setRequestProperty("Authorization", _authCode);
            conn.setRequestProperty("Content-Type", CONTENT_TYPE_JSON);

            if (null == content) {
                conn.setDoOutput(false);
            } else {
                conn.setDoOutput(true);
                byte[] data = content.getBytes(CHARSET);
                conn.setRequestProperty("Content-Length", String.valueOf(data.length));
                out = conn.getOutputStream();
                out.write(data);
                out.flush();
            }

            int status = conn.getResponseCode();
            if (status / 100 == 2) {
                in = conn.getInputStream();
            } else {
                in = conn.getErrorStream();
            }

            if (null != in) {
                reader = new InputStreamReader(in, CHARSET);
                char[] buff = new char[1024];
                int len;
                while ((len = reader.read(buff)) > 0) {
                    sb.append(buff, 0, len);
                }
            }

            String responseContent = sb.toString();
            wrapper.responseCode = status;
            wrapper.responseContent = responseContent;

            String quota = conn.getHeaderField(RATE_LIMIT_QUOTA);
            String remaining = conn.getHeaderField(RATE_LIMIT_Remaining);
            String reset = conn.getHeaderField(RATE_LIMIT_Reset);
            wrapper.setRateLimit(quota, remaining, reset);

            if (status >= 200 && status < 300) {
                LOG.debug("Succeed to get response OK - responseCode:" + status);
                LOG.debug("Response Content - " + responseContent);

            } else if (status >= 300 && status < 400) {
                LOG.warn("Normal response but unexpected - responseCode:" + status + ", responseContent:" + responseContent);

            } else {
                LOG.warn("Got error response - responseCode:" + status + ", responseContent:" + responseContent);

                switch (status) {
                    case 400:
                        LOG.error("Your request params is invalid. Please check them according to error message.");
                        wrapper.setErrorObject();
                        break;
                    case 401:
                        LOG.error("Authentication failed! Please check authentication params according to docs.");
                        wrapper.setErrorObject();
                        break;
                    case 403:
                        LOG.error("Request is forbidden! Maybe your appkey is listed in blacklist or your params is invalid.");
                        wrapper.setErrorObject();
                        break;
                    case 404:
                        LOG.error("Request page is not found! Maybe your params is invalid.");
                        wrapper.setErrorObject();
                        break;
                    case 410:
                        LOG.error("Request resource is no longer in service. Please according to notice on official website.");
                        wrapper.setErrorObject();
                    case 429:
                        LOG.error("Too many requests! Please review your appkey's request quota.");
                        wrapper.setErrorObject();
                        break;
                    case 500:
                    case 502:
                    case 503:
                    case 504:
                        LOG.error("Seems encountered server error. Maybe JPush is in maintenance? Please retry later.");
                        break;
                    default:
                        LOG.error("Unexpected response.");
                }

                throw new APIRequestException(wrapper);
            }

        } catch (SocketTimeoutException e) {
            if (e.getMessage().contains(KEYWORDS_CONNECT_TIMED_OUT)) {
                throw e;
            } else if (e.getMessage().contains(KEYWORDS_READ_TIMED_OUT)) {
                throw new SocketTimeoutException(KEYWORDS_READ_TIMED_OUT);
            }
            LOG.debug(IO_ERROR_MESSAGE, e);
            throw new APIConnectionException(IO_ERROR_MESSAGE, e);

        } catch (IOException e) {
            LOG.debug(IO_ERROR_MESSAGE, e);
            throw new APIConnectionException(IO_ERROR_MESSAGE, e);

        } finally {
            if (null != out) {
                try {
                    out.close();
                } catch (IOException e) {
                    LOG.error("Failed to close stream.", e);
                }
            }
            if (null != conn) {
                conn.disconnect();
            }
            if (null != in) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (null != reader) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return wrapper;
    }

    protected void initSSL(String sslVer) {
        TrustManager[] tmCerts = new TrustManager[1];
        tmCerts[0] = new SimpleTrustManager();
        try {
            SSLContext sslContext = SSLContext.getInstance(sslVer);
            sslContext.init(null, tmCerts, null);
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());

            HostnameVerifier hostnameVerifier = new SimpleHostnameVerifier();
            HttpsURLConnection.setDefaultHostnameVerifier(hostnameVerifier);
        } catch (Exception e) {
            LOG.error("Init SSL error", e);
        }
    }

    public String formUploadByPut(String urlStr, Map<String, String> textMap,
                                  Map<String, String> fileMap, String contentType) {
        return formUpload(urlStr, textMap, fileMap, contentType, "PUT");
    }

    public String formUploadByPost(String urlStr, Map<String, String> textMap,
                                   Map<String, String> fileMap, String contentType) {
        return formUpload(urlStr, textMap, fileMap, contentType, "POST");
    }

    private String formUpload(String urlStr, Map<String, String> textMap,
                              Map<String, String> fileMap, String contentType, String requestMethod) {
        String res = "";
        HttpURLConnection conn = null;
        // boundary就是request头和上传文件内容的分隔符
        String BOUNDARY = "---------------------------" + System.currentTimeMillis();
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(30000);
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setUseCaches(false);
            conn.setRequestMethod(requestMethod);
            conn.setRequestProperty("Connection", "Keep-Alive");
            conn.setRequestProperty("Authorization", _authCode);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);
            OutputStream out = new DataOutputStream(conn.getOutputStream());
            // text
            if (textMap != null) {
                StringBuffer strBuf = new StringBuffer();
                Iterator iter = textMap.entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry entry = (Map.Entry) iter.next();
                    String inputName = (String) entry.getKey();
                    String inputValue = (String) entry.getValue();
                    if (inputValue == null) {
                        continue;
                    }
                    strBuf.append("\r\n").append("--").append(BOUNDARY).append("\r\n");
                    strBuf.append("Content-Disposition: form-data; name=\"" + inputName + "\"\r\n\r\n");
                    strBuf.append(inputValue);
                }
                out.write(strBuf.toString().getBytes());
            }
            // file
            if (fileMap != null) {
                Iterator iter = fileMap.entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry entry = (Map.Entry) iter.next();
                    String inputName = (String) entry.getKey();
                    String inputValue = (String) entry.getValue();
                    if (inputValue == null) {
                        continue;
                    }
                    File file = new File(inputValue);
                    String filename = file.getName();

                    //没有传入文件类型，同时根据文件获取不到类型，默认采用application/octet-stream
                    contentType = new MimetypesFileTypeMap().getContentType(file);
                    //contentType非空采用filename匹配默认的图片类型
                    if (!"".equals(contentType)) {
                        if (filename.endsWith(".png")) {
                            contentType = "image/png";
                        } else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg") || filename.endsWith(".jpe")) {
                            contentType = "image/jpeg";
                        } else if (filename.endsWith(".gif")) {
                            contentType = "image/gif";
                        } else if (filename.endsWith(".ico")) {
                            contentType = "image/image/x-icon";
                        }
                    }
                    if (contentType == null || "".equals(contentType)) {
                        contentType = "application/octet-stream";
                    }
                    StringBuffer strBuf = new StringBuffer();
                    strBuf.append("\r\n").append("--").append(BOUNDARY).append("\r\n");
                    strBuf.append("Content-Disposition: form-data; name=\"" + inputName + "\"; filename=\"" + filename + "\"\r\n");
                    strBuf.append("Content-Type:" + contentType + "\r\n\r\n");
                    out.write(strBuf.toString().getBytes());
                    DataInputStream in = new DataInputStream(new FileInputStream(file));
                    int bytes = 0;
                    byte[] bufferOut = new byte[1024];
                    while ((bytes = in.read(bufferOut)) != -1) {
                        out.write(bufferOut, 0, bytes);
                    }
                    in.close();
                }
            }
            byte[] endData = ("\r\n--" + BOUNDARY + "--\r\n").getBytes();
            out.write(endData);
            out.flush();
            out.close();
            // 读取返回数据
            StringBuffer strBuf = new StringBuffer();

            InputStream is = null;
            int responseCode = conn.getResponseCode();
            BufferedReader reader;
            if (responseCode == 200) {
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            } else {
                reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
            }
            String line = null;
            while ((line = reader.readLine()) != null) {
                strBuf.append(line).append("\n");
            }
            res = strBuf.toString();
            reader.close();
            reader = null;
        } catch (FileNotFoundException e) {
            LOG.error("formUpload error", e);
            throw new RuntimeException("formUpload error", e);
        } catch (Exception e) {
            LOG.error("formUpload error", e);
        } finally {
            if (conn != null) {
                conn.disconnect();
                conn = null;
            }
        }
        return res;
    }

    private static class SimpleHostnameVerifier implements HostnameVerifier {

        public boolean verify(String hostname, SSLSession session) {
            return true;
        }

    }

    private static class SimpleTrustManager implements TrustManager, X509TrustManager {

        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            return;
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            return;
        }

        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }
    }

    public static class SimpleProxyAuthenticator extends Authenticator {
        private String username;
        private String password;

        public SimpleProxyAuthenticator(String username, String password) {
            this.username = username;
            this.password = password;
        }

        protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(
                    this.username,
                    this.password.toCharArray());
        }
    }
}
