package com.vesgar.belltracker

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import java.util.concurrent.Executors

class CaptureService:Service() {
 private val channel="bell_link_status"
 private var projection:MediaProjection?=null
 private var reader:ImageReader?=null
 private val executor=Executors.newSingleThreadExecutor()
 private val handler=Handler(Looper.getMainLooper())
 private var active=false
 private var previousCount=-1
 private var stable=0
 private var bonusLatched=false
 private var lastProcessed=0L
 private lateinit var detector:BellDetector
 override fun onBind(intent:Intent?)=null
 override fun onCreate(){ super.onCreate();detector=BellDetector(this)
  (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(channel,"Bell Link takip",NotificationManager.IMPORTANCE_DEFAULT))
 }
 private fun notification(message:String):Notification {
  val intent=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
  return Notification.Builder(this,channel).setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("Bell Link takip").setContentText(message).setContentIntent(intent).setOngoing(true).build()
 }
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
  if(intent?.action=="STOP") { stopSelf();return START_NOT_STICKY }
  if(active)return START_NOT_STICKY
  startForeground(11,notification("Ekran izleniyor"),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
  val data=if(Build.VERSION.SDK_INT>=33) intent?.getParcelableExtra("resultData",Intent::class.java) else @Suppress("DEPRECATION") intent?.getParcelableExtra<Intent>("resultData")
  if(data==null){stopSelf();return START_NOT_STICKY}
  val manager=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
  projection=manager.getMediaProjection(intent.getIntExtra("resultCode",Activity.RESULT_CANCELED),data)
  val display=resources.displayMetrics
  val w=display.widthPixels;val h=display.heightPixels
  reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2)
  projection?.registerCallback(object:MediaProjection.Callback(){override fun onStop(){stopSelf()}},handler)
  projection?.createVirtualDisplay("BellTracker",w,h,display.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader!!.surface,null,handler)
  active=true
  reader?.setOnImageAvailableListener({r ->
   val image=r.acquireLatestImage() ?: return@setOnImageAvailableListener
   val now=SystemClock.elapsedRealtime()
   if(now-lastProcessed<1100){image.close();return@setOnImageAvailableListener}
   lastProcessed=now
   try {
    val plane=image.planes[0];val stride=plane.rowStride/plane.pixelStride
    val full=Bitmap.createBitmap(stride,h,Bitmap.Config.ARGB_8888)
    full.copyPixelsFromBuffer(plane.buffer)
    val bitmap=Bitmap.createBitmap(full,0,0,w,h);full.recycle()
    executor.execute { try { process(bitmap) } finally { bitmap.recycle() } }
   }catch(_:Exception){} finally { image.close() }
  },handler)
  return START_NOT_STICKY
 }
 private fun process(bitmap:Bitmap){
  val bells=detector.count(bitmap)
  if(bells==previousCount)stable++ else {previousCount=bells;stable=1}
  if(stable<3)return
  val p=getSharedPreferences("tracker",0)
  val editor=p.edit().putInt("bells",bells).putString("status","Takip aktif")
  if(bells>=5 && !bonusLatched){
   bonusLatched=true;editor.putInt("bonuses",p.getInt("bonuses",0)+1)
   (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(12,Notification.Builder(this,channel).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("BONUS ADAYI: $bells ÇAN").setContentText("Ekranda en az 5 çan algılandı; oyun içinden doğrulayın.").setAutoCancel(true).build())
  }
  if(bells<5)bonusLatched=false
  editor.apply()
  (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(11,notification("Çan: $bells | Bonus kayıt: ${p.getInt("bonuses",0)}"))
 }
 override fun onDestroy(){active=false;reader?.close();reader=null;projection?.stop();projection=null;executor.shutdown();super.onDestroy()}
}
