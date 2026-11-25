package com.example.solitairefresh
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.widget.Toast
class KlondikeView(context: Context): View(context) {
 data class Card(val rank:Int,val suit:Int,var face:Boolean=false){ val red get()=suit==1||suit==2; fun label()=listOf("","A","2","3","4","5","6","7","8","9","10","J","Q","K")[rank]+listOf("♠","♥","♦","♣")[suit] }
 private val stock=mutableListOf<Card>(); private val waste=mutableListOf<Card>(); private val foundations=Array(4){mutableListOf<Card>()}; private val piles=Array(7){mutableListOf<Card>()}; private val p=Paint(3)
 private var cw=0f; private var ch=0f; private var gap=12f; private var top=16f; private var selectedPile=-1; private var selectedIndex=0; private var selectedWaste=false; private var hint="اسحب من المخزون أو اختر بطاقة"
 init{newGame()}
 fun newGame(){val deck=(0..3).flatMap{s->(1..13).map{Card(it,s)}}.shuffled();stock.clear();waste.clear();foundations.forEach{it.clear()};piles.forEach{it.clear()};var i=0;for(col in 0..6)for(r in 0..col){val c=deck[i++];c.face=r==col;piles[col].add(c)};stock.addAll(deck.drop(i));clearSel();hint="اضغط المخزون للسحب";invalidate()}
 fun showHint(){hint=if(waste.isNotEmpty())"جرّب بطاقة النفايات في الأعمدة أو الأساس" else "اكشف البطاقات المخفية، وبدّل الأحمر والأسود";Toast.makeText(context,hint,Toast.LENGTH_SHORT).show();invalidate()}
 override fun onDraw(c:Canvas){super.onDraw(c);c.drawColor(Color.rgb(15,105,64));cw=(width-8*gap)/7;ch=cw*1.38f
 drawBack(c,0f,top);drawEmpty(c,cw+gap,top)
 if(waste.isNotEmpty())drawCard(c,cw+gap,top,waste.last(),selectedWaste)
 for(s in 0..3)drawEmpty(c,(2+s)*(cw+gap),top,listOf("♠","♥","♦","♣")[s])
 val ty=top+ch+24
 for(col in 0..6){val x=col*(cw+gap);if(piles[col].isEmpty())drawEmpty(c,x,ty,"K");var y=ty;for(i in piles[col].indices){val card=piles[col][i];drawCard(c,x,y,card,selectedPile==col&&i>=selectedIndex);y+=if(card.face)28f else 17f}}
 p.color=Color.WHITE;p.textSize=15f;p.textAlign=Paint.Align.CENTER;c.drawText(hint.take(52),width/2f,height-10f,p)}
 private fun drawEmpty(c:Canvas,x:Float,y:Float,label:String=""){p.color=Color.WHITE;p.style=Paint.Style.STROKE;p.strokeWidth=2f;c.drawRoundRect(RectF(x,y,x+cw,y+ch),8f,8f,p);p.style=Paint.Style.FILL;if(label.isNotEmpty()){p.color=Color.argb(150,255,255,255);p.textSize=22f;p.textAlign=Paint.Align.CENTER;c.drawText(label,x+cw/2,y+ch/2+8,p)}}
 private fun drawBack(c:Canvas,x:Float,y:Float){p.color=Color.rgb(38,75,160);c.drawRoundRect(RectF(x,y,x+cw,y+ch),8f,8f,p);p.color=Color.WHITE;p.style=Paint.Style.STROKE;p.strokeWidth=3f;c.drawRoundRect(RectF(x+4,y+4,x+cw-4,y+ch-4),6f,6f,p);p.style=Paint.Style.FILL}
 private fun drawCard(c:Canvas,x:Float,y:Float,a:Card,chosen:Boolean){if(!a.face){drawBack(c,x,y);return};p.color=Color.WHITE;c.drawRoundRect(RectF(x,y,x+cw,y+ch),8f,8f,p);p.color=if(a.red)Color.rgb(190,20,40) else Color.BLACK;p.textAlign=Paint.Align.LEFT;p.textSize=(cw*.21f).coerceAtLeast(15f);c.drawText(a.label(),x+5,y+p.textSize+4,p);p.textAlign=Paint.Align.CENTER;p.textSize=(cw*.32f).coerceAtLeast(19f);c.drawText(listOf("♠","♥","♦","♣")[a.suit],x+cw/2,y+ch*.6f,p);if(chosen){p.color=Color.YELLOW;p.style=Paint.Style.STROKE;p.strokeWidth=3f;c.drawRoundRect(RectF(x,y,x+cw,y+ch),8f,8f,p);p.style=Paint.Style.FILL}}
 private fun canTableau(a:Card,b:List<Card>)=if(b.isEmpty())a.rank==13 else b.last().face&&b.last().red!=a.red&&b.last().rank==a.rank+1
 override fun onTouchEvent(e:android.view.MotionEvent):Boolean{if(e.action!=android.view.MotionEvent.ACTION_UP)return true;val x=e.x;val y=e.y;val ty=top+ch+24
 if(y<ty-10&&x<cw){if(stock.isNotEmpty()){val a=stock.removeAt(stock.lastIndex);a.face=true;waste.add(a)}else if(waste.isNotEmpty()){stock.addAll(waste.reversed().map{it.copy(face=false)});waste.clear()};clearSel();invalidate();return true}
 if(y<ty-10&&x>cw+gap&&x<2*(cw+gap)){if(waste.isNotEmpty()){selectedWaste=true;selectedPile=-1};invalidate();return true}
 if(y>=ty){val col=(x/(cw+gap)).toInt().coerceIn(0,6);val pile=piles[col];var idx=0;if(pile.isNotEmpty()){val dy=y-ty;var yy=0f;for(k in pile.indices){idx=k;if(dy<yy+(if(pile[k].face)28f else 17f))break;yy+=if(pile[k].face)28f else 17f}}
 if(selectedWaste||selectedPile>=0){val moving=if(selectedWaste)waste.lastOrNull() else piles[selectedPile].getOrNull(selectedIndex);if(moving!=null&&canTableau(moving,pile)){if(selectedWaste){waste.removeAt(waste.lastIndex);pile.add(moving)}else{val from=piles[selectedPile];val cards=from.drop(selectedIndex);from.subList(selectedIndex,from.size).clear();if(from.isNotEmpty()&&!from.last().face)from.last().face=true;pile.addAll(cards)};hint="تم النقل"}else hint="الحركة غير متاحة";clearSel()}else if(pile.isNotEmpty()&&pile[idx].face){selectedPile=col;selectedIndex=idx};invalidate();return true}
 clearSel();invalidate();return true}
 private fun clearSel(){selectedPile=-1;selectedIndex=0;selectedWaste=false}
}