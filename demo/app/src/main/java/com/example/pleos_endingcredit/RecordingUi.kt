package com.example.pleos_endingcredit

import android.content.Context
import android.graphics.*
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.res.ResourcesCompat
import com.caverock.androidsvg.SVG
import kotlin.math.min
import kotlin.math.roundToInt

/** Unmodified SVG exports from the requested A4 family. */
internal class RecordingAsset(context:Context,file:String):View(context) {
    private val picture=cache.getOrPut(file){context.assets.open("a4/$file").use{SVG.getFromInputStream(it)}.renderToPicture()}
    init {importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO}
    override fun onDraw(canvas:Canvas){canvas.drawPicture(picture,RectF(0f,0f,width.toFloat(),height.toFloat()))}
    companion object {private val cache=mutableMapOf<String,Picture>()}
}

internal fun recordingText(context:Context,copy:String,size:Float,scale:Float,weight:Int=400,color:Int=0xFF222222.toInt())=TextView(context).apply {
    text=copy;setTextColor(color);includeFontPadding=false;setPadding(0,0,0,0)
    typeface=ResourcesCompat.getFont(context,if(weight>=700)R.font.noto_sans_kr_bold else R.font.noto_sans_kr_regular)
    setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size*scale);maxLines=1
    ellipsize=TextUtils.TruncateAt.END;gravity=Gravity.CENTER_VERTICAL
}

/** App-only dim; cards retain their source dimensions and native focus. */
internal class RecordingOverlay(context:Context,panel:View,private val sourceW:Float,private val sourceH:Float,
    private val top:Float?=null,dismiss:()->Unit):FrameLayout(context) {
    private val card=panel.apply{isClickable=true};private val bounds=RectF()
    private val shadow=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE}
    private var scale=1f
    init {
        tag="a4.overlay";setBackgroundColor(0x59000000);setWillNotDraw(false)
        setLayerType(LAYER_TYPE_SOFTWARE,null);isClickable=true;isFocusableInTouchMode=true
        setOnClickListener{dismiss()};addView(card)
    }
    override fun onMeasure(w:Int,h:Int){
        val width=MeasureSpec.getSize(w);val height=MeasureSpec.getSize(h);setMeasuredDimension(width,height)
        scale=min(width/1700f,height/1184f)
        val cw=(sourceW*scale).roundToInt();val ch=(sourceH*scale).roundToInt()
        val y=top?.let{(height-1184*scale)/2+it*scale}?:((height-ch)/2f)
        bounds.set((width-cw)/2f,y,(width+cw)/2f,y+ch)
        card.measure(MeasureSpec.makeMeasureSpec(cw,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(ch,MeasureSpec.EXACTLY))
        shadow.setShadowLayer(16*scale,0f,12*scale,0x33000000)
    }
    override fun onLayout(changed:Boolean,l:Int,t:Int,r:Int,b:Int){card.layout(bounds.left.roundToInt(),bounds.top.roundToInt(),bounds.right.roundToInt(),bounds.bottom.roundToInt())}
    override fun onDraw(canvas:Canvas){super.onDraw(canvas);canvas.drawRoundRect(bounds,24*scale,24*scale,shadow)}
}

internal class RecordingCandidatesPanel(context:Context,private val stop:RecordedStop,place:String,index:Int,total:Int,
    private val close:()->Unit,private val later:()->Unit,private val choose:(PlaceCandidate?)->Unit
):ResultDesignSurface(context,1100f,824f) {
    private var options=emptyList<PlaceCandidate>();private var selected=0
    private val cards=mutableListOf<View>();private val icons=mutableListOf<FrameLayout>()
    private val names=mutableListOf<TextView>();private val addresses=mutableListOf<TextView>();private val distances=mutableListOf<TextView>()
    private val empty=label("a4.candidates.empty","주변 후보를 확인하고 있어요. 직접 이름을 남길 수도 있어요.",24f,color=muted)
    private val confirm=button("a4.candidates.confirm",if(index==total-1)"완료" else "여기예요",{
        if(selected==3)choose(null) else options.getOrNull(selected)?.let{choose(it)}
    },solid=true,size=28f)
    init {
        setTag(R.id.ending_credit_screen_id, "A4-1")
        tag="a4.candidates";accessibilityPaneTitle="어디에 들렀나요?"
        place(label("a4.candidates.title","어디에 들렀나요?",32f,700),44f,40f,650f,38f)
        place(label("a4.candidates.progress","${index+1} / $total",24f,500,0xFF737373.toInt()),886f,50f,80f,29f)
        if(total==2)place(RecordingAsset(context,"2dc4c.svg").apply{rotation=if(index==0)0f else 180f},948f,58f,40f,12f)
        place(RecordingAsset(context,"54976.svg").apply{importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES;isClickable=true;isFocusable=true;contentDescription="닫기";setOnClickListener{close()}},1016f,44f,40f,40f)
        place(box("a4.candidates.place",0xFFF5F5F5.toInt(),16f),44f,108f,1012f,120f)
        place(label("a4.candidates.place.title","${time(stop.at)} · $place",28f,700),72f,130f,720f,34f)
        place(label("a4.candidates.duration",dwell(stop),22f,color=0xFF737373.toInt()),72f,174f,720f,27f)
        place(RecordingAsset(context,"a8c99.svg"),824f,120f,220f,96f)
        place(empty,44f,270f,1012f,70f)
        repeat(3){i->
            val y=252f+i*112
            cards+=place(box("a4.candidates.row.$i",Color.WHITE,14f,1.5f,0xFFE0E0E0.toInt()).apply{importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES;isClickable=true;isFocusable=true;setOnClickListener{selected=i;updateSelection()}},44f,y,1012f,100f)
            val icon=FrameLayout(context);icons+=icon;place(icon,66.5f,y+34.5f,28f,28f)
            names+=label("a4.candidates.name.$i","",26f).also{place(it,112f,y+16f,760f,31f)}
            addresses+=label("a4.candidates.address.$i","",21f,color=0xFF808080.toInt()).also{place(it,112f,y+49f,760f,25f)}
            distances+=label("a4.candidates.distance.$i","",24f,500,0xFF737373.toInt()).apply{gravity=Gravity.END or Gravity.CENTER_VERTICAL}.also{place(it,930f,y+35.5f,100f,29f)}
        }
        cards+=place(box("a4.candidates.manual",Color.WHITE,14f,1.5f,0xFFE0E0E0.toInt()).apply{importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES;isClickable=true;isFocusable=true;contentDescription="직접 적을게요";setOnClickListener{selected=3;updateSelection()}},44f,588f,1012f,76f)
        val manualIcon=FrameLayout(context);icons+=manualIcon;place(manualIcon,66.5f,610.5f,28f,28f)
        place(label("a4.candidates.manual.label","직접 적을게요",26f),112f,610.5f,160f,31f)
        place(label("a4.candidates.manual.hint","목록에 없으면 가게 이름을 적어 주세요",20f,color=0xFF737373.toInt()),278f,614f,700f,24f)
        place(button("a4.candidates.later","나중에 할게요",later,outlined=true,size=28f),44f,696f,320f,88f)
        place(confirm,384f,696f,672f,88f)
        updateCandidates(emptyList())
    }
    fun updateCandidates(value:List<PlaceCandidate>){
        options=value.take(3);if(selected!=3&&selected>=options.size)selected=0
        repeat(3){i->
            val p=options.getOrNull(i);val visibility=if(p==null)GONE else VISIBLE
            listOf(cards[i],icons[i],names[i],addresses[i],distances[i]).forEach{it.visibility=visibility}
            if(p!=null){names[i].text=p.name;addresses[i].text=p.address.ifBlank{"주소 정보 없음"};distances[i].text="${p.distanceMeters.roundToInt()}m";cards[i].contentDescription="${p.name}, ${p.address}, ${p.distanceMeters.roundToInt()}미터"}
        }
        empty.visibility=if(options.isEmpty())VISIBLE else GONE
        updateSelection()
    }
    private fun updateSelection(){
        styleCards()
        icons.forEachIndexed{i,v->v.removeAllViews();v.addView(RecordingAsset(context,if(i==selected)"a22e6.svg" else "cb3f7.svg"),FrameLayout.LayoutParams(-1,-1))}
        confirm.isEnabled=selected==3||options.getOrNull(selected)!=null;requestLayout()
    }
    private fun styleCards(){cards.forEachIndexed{i,v->v.isSelected=i==selected;v.background=android.graphics.drawable.GradientDrawable().apply{setColor(if(i==selected)0xFFF2F2F2.toInt() else Color.WHITE);cornerRadius=14*drawingScale;setStroke(((if(i==selected)2f else 1.5f)*drawingScale).roundToInt().coerceAtLeast(1),if(i==selected)0xFF333333.toInt() else 0xFFE0E0E0.toInt())}}}
    override fun onMeasure(w:Int,h:Int){super.onMeasure(w,h);styleCards();names.forEachIndexed{i,v->v.typeface=ResourcesCompat.getFont(context,if(i==selected)R.font.noto_sans_kr_bold else R.font.noto_sans_kr_regular)}}
    companion object {
        fun time(at:Long)=java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()))
        fun dwell(stop:RecordedStop)=stop.departedAt?.let{"여기서 ${((it-stop.at).coerceAtLeast(0)/60_000)}분 있었어요"}?:"머문 시간 기록이 없어요"
    }
}

internal class RecordingNamePanel(context:Context,stop:RecordedStop,place:String,options:List<PlaceCandidate>,
    private val cancel:()->Unit,save:(String)->Unit,suggestion:(PlaceCandidate)->Unit,
    voice:((String)->Unit)->Unit
):ResultDesignSurface(context,1100f,823f) {
    val input=AppCompatEditText(context).apply {
        tag="a4.manual.input";hint="가게 이름";maxLines=1;setSingleLine();setPadding(0,0,0,0)
        setTextColor(0xFF333333.toInt());setHintTextColor(0xFF808080.toInt());setBackgroundColor(Color.TRANSPARENT)
        inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        filters=arrayOf(InputFilter.LengthFilter(20));imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_DONE
    }
    private val saveButton=button("a4.manual.save","저장하기",{val text=input.text.toString().trim();if(text.isEmpty())input.error="가게 이름을 입력해 주세요" else save(text)},solid=true,size=30f)
    init {
        setTag(R.id.ending_credit_screen_id, "A4-1-1")
        tag="a4.manual";accessibilityPaneTitle="가게 이름을 알려 주세요"
        place(label("a4.manual.title","가게 이름을 알려 주세요",34f,700,0xFF333333.toInt()),44f,104f-60.36f,900f,41f)
        val detail=stop.departedAt?.let{"${RecordingCandidatesPanel.time(stop.at)}, $place · ${((it-stop.at).coerceAtLeast(0)/60_000)}분 있었어요"}?:"${RecordingCandidatesPanel.time(stop.at)}, $place · 머문 시간 기록이 없어요"
        place(label("a4.manual.detail",detail,24f,color=0xFF737373.toInt()),44f,104.64f,1012f,29f)
        place(box("a4.manual.field",Color.WHITE,12f,3f,0xFF333333.toInt()),44f,154f,1012f,82f)
        place(input,71f,174f,890f,42f)
        place(RecordingAsset(context,"880ee.svg").apply{tag="a4.manual.voice";importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES;isClickable=true;isFocusable=true;contentDescription="음성으로 가게 이름 입력";setOnClickListener{voice{value->if(isAttachedToWindow){input.setText(value.take(20));input.setSelection(input.length())}}}},993f,177f,36f,36f)
        options.take(2).forEachIndexed{i,p->
            val y=256f+i*93.14f
            place(button("a4.manual.suggestion.$i","",{suggestion(p)}).apply{contentDescription="${p.name}, ${p.address}, ${p.distanceMeters.roundToInt()}미터"},44f,y-6f,1012f,80f)
            place(label("a4.manual.suggestion.name.$i","● ${p.name}",26f),50f,y-13f,850f,38f)
            place(label("a4.manual.suggestion.address.$i",p.address.ifBlank{"주소 정보 없음"},22f,color=muted),79.5f,y+27f,830f,34f)
            place(label("a4.manual.suggestion.distance.$i","${p.distanceMeters.roundToInt()}m",27f,700).apply{gravity=Gravity.END or Gravity.CENTER_VERTICAL},970f,y+16f,80f,40f)
            place(box("a4.manual.suggestion.line.$i",0xFFDDDDDD.toInt()),44f,y+71.14f,1012f,2f)
        }
        place(button("a4.manual.cancel","취소",cancel,outlined=true,size=30f),681f,444f,160f,88f)
        place(saveButton,857f,444f,199f,88f)
        place(label("a4.manual.hint","적은 이름이 크레딧과 여행 카드에 그대로 나와요. (20자까지)",22f,color=0xFF737373.toInt()),44f,622.09f,1012f,27f)
        input.setOnEditorActionListener{_,action,_->if(action==android.view.inputmethod.EditorInfo.IME_ACTION_DONE){saveButton.performClick();true}else false}
    }
    fun openKeyboard(){input.requestFocus();input.post{if(input.isAttachedToWindow)(context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT)}}
    override fun onMeasure(w:Int,h:Int){super.onMeasure(w,h);input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,30*drawingScale);input.typeface=ResourcesCompat.getFont(context,R.font.noto_sans_kr_regular)}
}

internal class RecordingEndPanel(context:Context,distance:String,count:Int,cancel:()->Unit,finish:()->Unit):ResultDesignSurface(context,900f,350.8f){
    init {
        setTag(R.id.ending_credit_screen_id, "A4-3")
        tag="a4.finish";accessibilityPaneTitle="여행을 끝낼까요?"
        place(label("a4.finish.title","여행을 끝낼까요?",32f,700),44f,43.8f,812f,46.4f)
        place(label("a4.finish.body","지금까지 달린 ${distance}와 다녀온 곳 ${count}곳으로 크레딧을 만들어요.",26f,color=muted),44f,106.8f,812f,37.7f)
        place(label("a4.finish.body.warning","끝내면 기록을 이어갈 수 없어요.",26f,color=muted),44f,144.5f,812f,37.7f)
        place(button("a4.finish.cancel","아직이에요",cancel,outlined=true,size=30f),339f,214.8f,237f,96f)
        place(button("a4.finish.confirm","끝낼게요",finish,solid=true,size=30f),592f,214.8f,264f,96f)
    }
}
