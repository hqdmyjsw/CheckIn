package com.checkin.service;

import java.util.Timer;
import java.util.TimerTask;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import com.checkin.MainActivity;
import com.checkin.R;
import com.checkin.utils.PreferGeter;
import com.checkin.utils.SocketUtil;

/**
 * 后台发送签到信息主服务， 判断是否能与服务器连接并发送消息
 * 
 * @author Administrator
 * 
 */
public class MyService extends Service {

	static int intCounter;
	static boolean runFlag = true;
	static final int DELAY = 2 * 60 * 1000; // 刷新频率2分钟
	static int noSignCounter;
	static final String UPDATE_ACTION = "com.checkin.updateui";// 更新前台UI
	private String tag = "MyService";
	private Intent in;
	private Timer timer;
	private WakeLockManger wm = new WakeLockManger(this); // CPU锁

	public static boolean isCheck = false;
	
	// 接受网络检测信号返回值并更新UI线程
	Handler hd = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case 0:
				Log.i(tag, "发送首次签到成功通知");
				showNotif(MyService.this, true);
				in = new Intent();
				in.setAction(UPDATE_ACTION);
				in.putExtra("state", 0);
				sendBroadcast(in);
				isCheck = true;
				break;
			case 1:
				Log.i(tag, "发送离开通知通知");
				showNotif(MyService.this, false);
				in = new Intent();
				in.setAction(UPDATE_ACTION);
				in.putExtra("state", 1);
				sendBroadcast(in);
				isCheck = false;
				break;

			}
		}
	};

	@Override
	public void onCreate() {

		super.onCreate();
		Log.i(tag, "onCreate()启动服务");
		timer = new Timer();
		TimerTask task = new ScanTask(this);
		timer.scheduleAtFixedRate(task, 0, DELAY);
		wm.acquireWakeLock();

	}

	@Override
	public void onStart(Intent intent, int startId) {
		// TODO Auto-generated method stub

		super.onStart(intent, startId);
		Log.i(tag, "onStart");
		noSignCounter = intCounter = 0;
		runFlag = true;

	}

	@Override
	public IBinder onBind(Intent intent) {
		Log.i(tag, "onBind()");
		return null;
	}

	@Override
	public void onDestroy() {

		// TODO Auto-generated method stub
		super.onDestroy();
		Log.i(tag, "onDestroy");
		if (timer != null) {
			timer.cancel();
		}
		wm.releaseWakeLock();

	}

	/**
	 * 后台扫描通信线程
	 * 
	 * @author Administrator
	 * 
	 */
	public class ScanTask extends TimerTask {

		private SocketUtil connect;
		private PreferGeter geter;
		private String ip;
		private String username, password, workcode;
		boolean get;
		private Context context;

		public ScanTask(Context con) {
			this.context = con;
		}

		@Override
		public void run() {
			// Looper.prepare();
			if (!runFlag) {
				return;
			}
			geter = new PreferGeter(context);
			ip = geter.getIP();
			username = geter.getUnm();
			password = geter.getPwd();
			workcode = geter.getWcd();

			get = false;
			intCounter++;
			Log.i("CheckIn", "Service Counter:" + Integer.toString(intCounter));
			connect = new SocketUtil(ip);
			if (!connect.isConnected) {
				try {
					connect.connectServer();
					get = connect.sendCheck(username, password, workcode);
					connect.close();
					if (!get) {
						noSignCounter++;
					}
				} catch (Exception e) {
					e.printStackTrace();
					Log.i(tag, "noSignCounter=" + noSignCounter);
					noSignCounter++; // 连接失败次数统计
				}
			}

			if (!isCheck && get) { // 首次签到
				Log.i(tag, "首次签到");
				Message tempMessage = new Message();
				tempMessage.what = 0;
				MyService.this.hd.sendMessage(tempMessage);
			}
			if (isCheck && !get) { // 离开
				Log.i(tag, "离开");
				Message tempMessage = new Message();
				tempMessage.what = 1;
				MyService.this.hd.sendMessage(tempMessage);
			}

			// 连续5次无响应，则结束服务
			if (noSignCounter >= 5) {

				Log.i(tag, "结束服务指令");
				runFlag = false;
				timer.cancel();
				MyService.this.stopSelf();
			}

		}
	}

	/**
	 * 签到和离开的通知栏显示
	 * 
	 * @param context
	 * @param isArrive
	 *            是否为签到成功通知
	 */
	public void showNotif(Context context, boolean isArrive) {

		// 定义通知栏展现的内容信息
		CharSequence title, contentTitle, contentText;
		if (isArrive) {
			title = "签到成功";
			contentTitle = "签到成功";
			contentText = "您已成功注册到811";

		} else {
			title = "离开";
			contentTitle = "离开";
			contentText = "您已离开811";
		}

		// 消息通知栏
		// 定义NotificationManager
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		int icon = R.drawable.ic_launcher;

		long when = System.currentTimeMillis();
		Notification notification = new Notification(icon, title, when);
		notification.defaults |= Notification.DEFAULT_SOUND;
		notification.flags |= Notification.FLAG_AUTO_CANCEL;

		// 定义下拉通知栏时要展现的内容信息
		Intent in = new Intent(context, MainActivity.class);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, in, 0);
		notification.setLatestEventInfo(context, contentTitle, contentText,
				contentIntent);

		// 用mNotificationManager的notify方法通知用户生成标题栏消息通知
		mNotificationManager.notify(1, notification);

	}

}