package com.vesgar.belltracker

import android.app.*
import android.content.*
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {
 private val requestCode = 4401
 private lateinit var status: TextView
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(36,60,36,30); gravity=Gravity.CENTER_HORIZONTAL }
  val title=TextView(this).apply { text="BELL LINK 40 SUPER HOT\nBONUS TAKİP"; textSize=23f; gravity=Gravity.CENTER }
  status=TextView(this).apply { text="Henüz takip başlamadı"; textSize=17f; setPadding(0,40,0,40) }
  val start=Button(this).apply { text="Ekran takibini başlat"; setOnClickListener { val m=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager; startActivityForResult(m.createScreenCaptureIntent(),requestCode) } }
  val stop=Button(this).apply { text="Takibi durdur"; setOnClickListener { stopService(Intent(this@MainActivity,CaptureService::class.java)) } }
  val info=TextView(this).apply { text="Prototip: yalnızca gönderdiğiniz ekranla aynı 691×1536 oranındaki oyun görüntüsünde çalışacak şekilde kalibre edilmiştir. Oyun ekranı değişirse yanlış tespit olabilir. 5 çan sonucu yalnızca görünür makaralardan doğrulanır." }
  root.addView(title);root.addView(status);root.addView(start);root.addView(stop);root.addView(info)
  setContentView(root)
 }
 override fun onResume(){super.onResume(); val p=getSharedPreferences("tracker",0); status.text="Son çan sayısı: ${p.getInt("bells",0)}\nBonus kayıtları: ${p.getInt("bonuses",0)}\nDurum: ${p.getString("status","Hazır")}" }
 @Deprecated("Legacy Activity result API") override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==this.requestCode && resultCode==RESULT_OK && data!=null) {
   val i=Intent(this,CaptureService::class.java).apply { putExtra("resultCode",resultCode);putExtra("resultData",data) }
   startForegroundService(i); status.text="Ekran takibi başlatılıyor..."
  }
 }
}
