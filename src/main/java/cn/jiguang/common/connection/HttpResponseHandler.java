/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package cn.jiguang.common.connection;

import cn.jiguang.common.resp.ResponseWrapper;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.AbstractMap.SimpleEntry;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

/**
 * Process {@link FullHttpResponse} translated from HTTP/2 frames
 */
public class HttpResponseHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

    private static final Logger LOG = LoggerFactory.getLogger(HttpResponseHandler.class);

    private SortedMap<Integer, Entry<ChannelFuture, ChannelPromise>> streamidPromiseMap;
    private NettyHttp2Client mNettyHttp2Client;
    private NettyHttp2Client.BaseCallback mCallback;

    public HttpResponseHandler(NettyHttp2Client nettyHttp2Client) {
        mNettyHttp2Client = nettyHttp2Client;
        streamidPromiseMap = new TreeMap<Integer, Entry<ChannelFuture, ChannelPromise>>();
    }

    /**
     * Create an association between an anticipated response stream id and a {@link ChannelPromise}
     *
     * @param streamId The stream for which a response is expected
     * @param writeFuture A future that represent the request write operation
     * @param promise The promise object that will be used to wait/notify events
     * @return The previous object associated with {@code streamId}
     * @see HttpResponseHandler#awaitResponses(long, TimeUnit)
     */
    public Entry<ChannelFuture, ChannelPromise> put(int streamId, ChannelFuture writeFuture, ChannelPromise promise) {
        return streamidPromiseMap.put(streamId, new SimpleEntry<ChannelFuture, ChannelPromise>(writeFuture, promise));
    }

    /**
     * Wait (sequentially) for a time duration for each anticipated response
     *
     * @param timeout Value of time to wait for each response
     * @param unit Units associated with {@code timeout}
     * @see HttpResponseHandler#put(int, ChannelFuture, ChannelPromise)
     */
    public void awaitResponses(long timeout, TimeUnit unit) {
        Iterator<Entry<Integer, Entry<ChannelFuture, ChannelPromise>>> itr = streamidPromiseMap.entrySet().iterator();
        while (itr.hasNext()) {
            Entry<Integer, Entry<ChannelFuture, ChannelPromise>> entry = itr.next();
            ChannelFuture writeFuture = entry.getValue().getKey();
            if (!writeFuture.awaitUninterruptibly(timeout, unit)) {
                throw new IllegalStateException("Timed out waiting to write for stream id " + entry.getKey());
            }
            if (!writeFuture.isSuccess()) {
                throw new RuntimeException(writeFuture.cause());
            }
            ChannelPromise promise = entry.getValue().getValue();
            if (!promise.awaitUninterruptibly(timeout, unit)) {
                throw new IllegalStateException("Timed out waiting for response on stream id " + entry.getKey());
            }
            if (!promise.isSuccess()) {
                throw new RuntimeException(promise.cause());
            }
            System.out.println("---Stream id: " + entry.getKey() + " received---");
            System.out.println("---Stream id: " + promise.toString());
            itr.remove();
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) throws Exception {
        Integer streamId = msg.headers().getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text());
        if (streamId == null) {
            System.err.println("HttpResponseHandler unexpected message received: " + msg);
            return;
        }

        Entry<ChannelFuture, ChannelPromise> entry = streamidPromiseMap.get(streamId);
        List<String> list = new ArrayList<String>();
        list.add(msg.status().code() + "");
        if (entry == null) {
            System.err.println("Message received for unknown stream id " + streamId);
        } else {
            // Do stuff with the message (for now just print it)
            ByteBuf byteBuf = msg.content();
            if (byteBuf.isReadable()) {
                int contentLength = byteBuf.readableBytes();
                byte[] arr = new byte[contentLength];
                byteBuf.readBytes(arr);
                String content = new String(arr, 0, contentLength, CharsetUtil.UTF_8);
                list.add(content);
            }

            mNettyHttp2Client.setResponse(streamId + ctx.channel().id().asShortText(), list);
            if (null != mCallback) {
                ResponseWrapper wrapper = new ResponseWrapper();
                wrapper.responseCode = Integer.valueOf(list.get(0));
                if (list.size() > 1) {
                    wrapper.responseContent = list.get(1);
                }
                mCallback.onSucceed(wrapper);
            }

            entry.getValue().setSuccess();
            if (msg instanceof HttpContent) {
                HttpContent content = (HttpContent) msg;
                System.err.println("content: " + content.content().toString(CharsetUtil.UTF_8));
                System.err.flush();

                if (content instanceof LastHttpContent) {
                    System.err.println(" end of content");
//                    ctx.close();
                }
            }

        }
    }

    public void setCallback(NettyHttp2Client.BaseCallback callback) {
        mCallback = callback;
    }

}
