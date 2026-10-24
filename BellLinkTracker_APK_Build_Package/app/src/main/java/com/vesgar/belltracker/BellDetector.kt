package com.vesgar.belltracker

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Context
import kotlin.math.*

/** Reference-derived prototype, not a trained image classifier. */
class BellDetector(context:Context) {
 private val ref=BitmapFactory.decodeStream(context.assets.open("reference.png"))
 private val sampleCells=listOf(2 to 0,1 to 1,3 to 1)
 private val negativeCells=listOf(0 to 0,4 to 0,2 to 1,1 to 2,3 to 2,4 to 3)
 private val bellSamples=sampleCells.map { crop(ref,it.first,it.second) }
 private val otherSamples=negativeCells.map { crop(ref,it.first,it.second) }
 private fun crop(b:Bitmap,c:Int,r:Int):DoubleArray {
  // Inner crop omits grid borders; original reference reel bounds x=0..690 y=398..779.
  val x0=(c*138+18).coerceAtMost(b.width-1); val y0=(398+r*95+9).coerceAtMost(b.height-1)
  val w=100;val h=76; val out=DoubleArray(24*24)
  for(j in 0 until 24) for(i in 0 until 24) {
   val x=(x0+(i+.5)*w/24).toInt().coerceIn(0,b.width-1)
   val y=(y0+(j+.5)*h/24).toInt().coerceIn(0,b.height-1)
   val p=b.getPixel(x,y); val red=(p shr 16) and 255;val green=(p shr 8) and 255;val blue=p and 255
   // Bell is gold/yellow, unlike purple fruit backdrop; retain spatial gold mask.
   out[j*24+i]=if(red>110 && red>blue*1.35 && green>blue*1.10 && green>55) 1.0 else 0.0
  }
  return out
 }
 private fun similarity(a:DoubleArray,b:DoubleArray):Double = 1.0-a.indices.count { a[it]!=b[it] }.toDouble()/a.size
 fun count(screen:Bitmap):Int {
  val normalized=Bitmap.createScaledBitmap(screen,691,1536,true)
  var count=0
  for(r in 0..3) for(c in 0..4) {
   val s=crop(normalized,c,r)
   val bell=bellSamples.maxOf { similarity(s,it) }
   val other=otherSamples.maxOf { similarity(s,it) }
   // Conservative relative margin; real-world calibration required.
   if(bell>=0.83 && bell-other>=0.035) count++
  }
  if(normalized!==screen) normalized.recycle()
  return count
 }
}
