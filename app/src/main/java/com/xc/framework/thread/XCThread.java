package com.xc.framework.thread;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.xc.framework.util.XCAppUtil;
import com.xc.framework.util.XCThreadUtil;

/**
 * @author ZhangXuanChen
 * @date 2020/3/1
 * @package com.xc.framework.other
 * @description 自定义线程
 */
public abstract class XCThread extends Thread {
    private boolean isRun = false;

    public XCThread() {
        init();
    }

    /**
     * @author ZhangXuanChen
     * @date 2020/3/3
     * @description init
     */
    private void init() {
        setName(XCAppUtil.getUUId());
        XCThreadUtil.getInstance().addThreadList(getName(), this);
    }

    /**
     * @author ZhangXuanChen
     * @date 2020/2/8
     * @description xcHandler
     */
    protected Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            onHandler(msg);
        }
    };


    @Override
    public void run() {
        super.run();
        Object obj = onRun(handler);
        if (obj != null) {
            sendMessage(0x789, obj);
        }
    }

    /**
     * @author ZhangXuanChen
     * @date 2020/3/8
     * @description sendMessage
     */
    public void sendMessage(int what) {
        sendMessage(what, null);
    }

    /**
     * @author ZhangXuanChen
     * @date 2020/3/8
     * @description sendMessage
     */
    public void sendMessage(int what, Object obj) {
        Message msg = handler.obtainMessage();
        msg.what = what;
        if (obj != null) {
            msg.obj = obj;
        }
        handler.sendMessage(msg);
    }
    /**
     * @author ZhangXuanChen
     * @date 2020/3/1
     * @description isRun
     */
    public boolean isRun() {
        return isRun;
    }

    /**
     * Author：ZhangXuanChen
     * Time：2020/3/13 9:48
     * Description：setRun
     */
    public void setRun(boolean run) {
        isRun = run;
    }
    /**
     * @author ZhangXuanChen
     * @date 2020/3/1
     * @description startThread
     */
    public void startThread() {
        isRun = true;
        start();
    }

    /**
     * @author ZhangXuanChen
     * @date 2020/3/1
     * @description stopThread
     */
    public void stopThread() {
        isRun = false;
        XCThreadUtil.getInstance().stopSingle(getName());
    }

    /**
     * @param
     * @return
     * @author ZhangXuanChen
     * @date 2020/3/3
     * @description run
     */
    protected abstract Object onRun(Handler handler);

    /**
     * @param
     * @return
     * @author ZhangXuanChen
     * @date 2020/3/3
     * @description handler
     */
    protected abstract void onHandler(Message msg);
}
