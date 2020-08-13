package com.xc.framework.port.core;

import android.util.Log;

import com.xc.framework.util.XCByteUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Date：2019/11/25
 * Author：ZhangXuanChen
 * Description：串口管理基类
 */
public abstract class PortManager {
    private final String TAG = "PortManager";
    private List<OnPortReceiveRequestListener> portReceiveRequestListenerList;//接收请求监听集合
    private PortReceiveThread mPortReceiveThread;//接收线程
    private ExecutorService mExecutorService;//发送线程池
    private LinkedBlockingQueue<PortSendCallable> mLinkedBlockingQueue;//正在执行的发送线程
    private boolean isOpen = false;

    public PortManager() {
        portReceiveRequestListenerList = new ArrayList<OnPortReceiveRequestListener>();
        initPool();
    }

    /**
     * Author：ZhangXuanChen
     * Time：2019/11/27 15:14
     * Description：IPort
     */
    public abstract IPort getIPort();

    /**
     * Author：ZhangXuanChen
     * Time：2019/11/27 15:14
     * Description：PortParam
     */
    public abstract PortParam getPortParam();

    /**
     * Author：ZhangXuanChen
     * Time：2020/3/27 13:25
     * Description：initPool
     */
    private void initPool() {
        mLinkedBlockingQueue = new LinkedBlockingQueue<PortSendCallable>(1);
        mExecutorService = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), Executors.defaultThreadFactory(), new ThreadPoolExecutor.DiscardPolicy());
    }

    /**
     * Author：ZhangXuanChen
     * Time：2019/11/25 16:01
     * Description：串口打开
     * Return：boolean
     */
    public boolean open() {
        if (getIPort() != null && getPortParam() != null) {
            isOpen = getIPort().openPort(getPortParam());
            if (isOpen) {
                initPool();
                startReceivedThread();
            }
        }
        return isOpen;
    }

    /**
     * Author：ZhangXuanChen
     * Time：2019/11/25 15:45
     * Description：串口关闭
     */
    public boolean close() {
        if (getIPort() != null) {
            getIPort().closePort();
        }
        if (mLinkedBlockingQueue != null) {
            mLinkedBlockingQueue.clear();
            mLinkedBlockingQueue = null;
        }
        if (mExecutorService != null) {
            mExecutorService.shutdown();
            mExecutorService = null;
        }
        if (mPortReceiveThread != null) {
            mPortReceiveThread.stopThread();
            mPortReceiveThread = null;
        }
        isOpen = false;
        return true;
    }

    /**
     * Author：ZhangXuanChen
     * Time：2020/3/10 9:25
     * Description：startReceivedTask
     */
    private void startReceivedThread() {
        mPortReceiveThread = new PortReceiveThread(getPortParam(), getIPort()) {
            @Override
            public void onResponse(byte[] responseDatas) {
                PortSendCallable portSendCallable = mLinkedBlockingQueue.peek();
                if (portSendCallable != null) {
                    portSendCallable.setResponseDatas(responseDatas);
                }
            }

            @Override
            public void onInterrupt(byte[] interruptDatas) {
                PortSendCallable portSendCallable = mLinkedBlockingQueue.peek();
                if (portSendCallable != null) {
                    portSendCallable.setInterruptDatas(interruptDatas);
                }
            }

            @Override
            public void onRequest(byte[] requestDatas) {
                if (portReceiveRequestListenerList != null && !portReceiveRequestListenerList.isEmpty()) {
                    for (OnPortReceiveRequestListener listener : portReceiveRequestListenerList) {
                        if (listener != null) {
                            listener.onRequest(requestDatas);
                        }
                    }
                }
            }
        };
        mPortReceiveThread.setDaemon(true);
        mPortReceiveThread.startThread();
    }

    /**
     * Author：ZhangXuanChen
     * Time：2020/8/5 17:05
     * Description：串口发送-直接串口写入
     * Param：bytes 发送数据
     */
    public void send(final byte[] bytes) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                getIPort().writePort(bytes);
                Log.i(TAG, "指令-直接发送:[" + XCByteUtil.toHexStr(bytes, true) + "]");
            }
        }).start();
    }

    /**
     * Author：ZhangXuanChena
     * Time：2020/7/11 16:48
     * Description：串口发送-阻塞
     * Param：bytes 发送数据
     * Param：receiveType 接收类型
     */
    public byte[] send(byte[] bytes, PortReceiveType receiveType) {
        return send(bytes, receiveType, true, -1, null);
    }

    /**
     * Author：ZhangXuanChen
     * Time：2019/11/27 16:15
     * Description：串口发送-异步
     * Param：bytes 发送数据
     * Param：receiveType 接收类型
     * Param：what 区分消息
     * Param：receiveResponseCallback 异步发送接收回调
     */
    public void send(byte[] bytes, PortReceiveType receiveType, int what, PortReceiveCallback portReceiveCallback) {
        send(bytes, receiveType, false, what, portReceiveCallback);
    }

    /**
     * Author：ZhangXuanChen
     * Time：2019/11/27 16:15
     * Description：串口发送
     * Param：bytes 发送数据
     * Param：receiveType 接收类型
     * Param：isBlockSend 是否阻塞发送
     * Param：what 区分消息
     * Param：receiveResponseCallback 异步发送接收回调
     */
    private byte[] send(byte[] bytes, PortReceiveType receiveType, boolean isBlockSend, int what, final PortReceiveCallback portReceiveCallback) {
        if (mExecutorService == null || mExecutorService.isShutdown()) {
            return null;
        }
        try {
            Future<byte[]> mFuture = mExecutorService.submit(new PortSendCallable(bytes, receiveType, what, getPortParam(), getIPort(), mLinkedBlockingQueue) {
                @Override
                public void onResponse(int what, byte[] responseDatas) {
                    mLinkedBlockingQueue.poll();
                    if (portReceiveCallback != null) {
                        portReceiveCallback.onResponse(what, responseDatas);
                    }
                }

                @Override
                public void onInterrupt(int what, byte[] interruptDatas) {
                    mLinkedBlockingQueue.poll();
                    if (portReceiveCallback != null) {
                        portReceiveCallback.onInterrupt(what, interruptDatas);
                    }
                }

                @Override
                public void onTimeout(int what, byte[] sendDatas) {
                    mLinkedBlockingQueue.poll();
                    if (portReceiveCallback != null) {
                        portReceiveCallback.onTimeout(what, sendDatas);
                    }
                }
            });
            if (isBlockSend) {
                return mFuture.get();
            }
        } catch (Exception e) {
        }
        return null;
    }

    /**
     * Author：ZhangXuanChen
     * Time：2020/8/5 8:42
     * Description：清空发送
     */
    public void clearSend() {
        if (mLinkedBlockingQueue != null) {
            mLinkedBlockingQueue.clear();
            mLinkedBlockingQueue = null;
        }
        if (mExecutorService != null) {
            mExecutorService.shutdownNow();
            mExecutorService = null;
        }
        initPool();
    }

    /**
     * Author：ZhangXuanChen
     * Time：2019/11/26 14:07
     * Description：设置接收请求监听
     */
    public void setOnPortReceiveRequestListener(OnPortReceiveRequestListener onPortReceiveRequestListener) {
        if (portReceiveRequestListenerList != null) {
            portReceiveRequestListenerList.add(onPortReceiveRequestListener);
        }
    }

    /**
     * Author：ZhangXuanChen
     * Time：2020/7/14 12:26
     * Description：移除接收请求监听
     */
    public void removeOnPortReceiveRequestListener(OnPortReceiveRequestListener onPortReceiveRequestListener) {
        if (portReceiveRequestListenerList != null && !portReceiveRequestListenerList.isEmpty()) {
            portReceiveRequestListenerList.remove(onPortReceiveRequestListener);
        }
    }

    /**
     * Author：ZhangXuanChen
     * Time：2020/7/14 12:26
     * Description：清空接收请求监听
     */
    public void clearOnPortReceiveRequestListener() {
        if (portReceiveRequestListenerList != null && !portReceiveRequestListenerList.isEmpty()) {
            portReceiveRequestListenerList.clear();
        }
    }
}
