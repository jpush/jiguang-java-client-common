# JiGuang Common for Java

Common lib for JiGuang Java clients. 

## 概述

这是极光 Java client 的公共封装开发包，为 jpush, jmessage, jsms 等 client 提供公共依赖。

此分支提供使用 Netty 第三方工具发送请求功能。目前需要以 jar 方式引入，资源可以在 libs 下得到。

## 使用
首先要调用 NettyHttp2Client 的构造函数（之前用的是 NativeHttpClient）：
```
NettyHttp2Client client = new NettyHttp2Client(authCode, proxy, conf, hostName);
```
前三个参数和 NativeHttpClient 一样，最后一个参数要传入一个主机域名。

发送单个请求的做法和之前一致，可以参考 [jpush-api-java-client](https://github.com/jpush/jpush-api-java-client/tree/http2) 下 example 中的相关示例。NettyHttp2Client 新增发送批量请求的接口:
```
NettyHttp2Client.setRequest(HttpMethod, Queue<Http2Request>).execute(BaseCallback)
```
示例代码如下：
```
public void testSendPushesReuse() {
        ClientConfig config = ClientConfig.getInstance();
        String host = (String) config.get(ClientConfig.PUSH_HOST_NAME);
        NettyHttp2Client client = new NettyHttp2Client(ServiceHelper.getBasicAuthorization(APP_KEY, MASTER_SECRET),
                null, config, host);
        Queue<Http2Request> queue = new LinkedList<Http2Request>();
        String url = (String) config.get(ClientConfig.PUSH_PATH);
        PushPayload payload = buildPushObject_all_all_alert();
        for (int i=0; i<100; i++) {
            queue.offer(new Http2Request(url, payload.toString()));
        }
        try {
            long before = System.currentTimeMillis();
            LOG.info("before: " + before);
            client.setRequestQueue(HttpMethod.POST, queue).execute(new NettyHttp2Client.BaseCallback() {
                @Override
                public void onSucceed(ResponseWrapper wrapper) {
                    PushResult result = BaseResult.fromResponse(wrapper, PushResult.class);
                    LOG.info("Got result - " + result);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
```
此为链接复用的接口，相对于循环发送单个请求，效率明显提高。可以参考 [PushClientTest](https://github.com/jpush/jpush-api-java-client/blob/http2/src/test/java/cn/jpush/api/push/PushClientTest.java)，写一下测试用例验证一下。

