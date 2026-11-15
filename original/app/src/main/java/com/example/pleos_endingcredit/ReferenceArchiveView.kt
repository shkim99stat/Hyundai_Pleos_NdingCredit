package com.example.pleos_endingcredit

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.withClip
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/** The requested Connect-S-17 archive, source frame 96:21524 / content 96:21526.
 * Geometry uses the 1700 x 1184 application window; Pleos owns the surrounding UI.
 * Photos and the overflow vector are extracted original assets. Trip names,
 * dates and the resume position come from the store rather than sample history.
 */
@SuppressLint("ViewConstructor")
internal class ReferenceArchiveView(
    context: Context,
    trips: List<Trip>,
    selectedYear: Int? = null,
    pending: Trip? = null,
    pendingFrame: Int = 0,
    onStart: () -> Unit = {},
    onClose: () -> Unit = {},
    onOpen: (Trip) -> Unit = {},
    onResume: (Int?) -> Unit = {},
    onSelectYear: (Int?) -> Unit = {},
    onReview: () -> Unit = {},
    onRename: (Trip, String) -> Unit = { _, _ -> },
) : ViewGroup(context) {
    private data class Placement(val view: View, val x: Float, val y: Float,
        val width: Float, val height: Float, val params: FrameLayout.LayoutParams = FrameLayout.LayoutParams(0,0))
    private data class Typography(val view: TextView, val size: Float, val height: Float, val baseline: Float)
    private data class Shape(val drawable: GradientDrawable, val radius: Float, val stroke: Float, val color: Int)

    private val placements = mutableListOf<Placement>()
    private val typography = mutableListOf<Typography>()
    private val shapes = mutableListOf<Shape>()
    private val rootViews = mutableListOf<Placement>()
    private val photos = mutableMapOf<Int, Bitmap>()
    private val surface = RectF()
    private val edge = RectF()
    private val paper = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0xFFD4D4D4.toInt(); style=Paint.Style.STROKE }
    private var scale = 1f
    private var dialog: AlertDialog? = null
    private var menu: PopupMenu? = null
    private val visibleTrips = trips.filter { selectedYear == null || it.createdOn.startsWith("$selectedYear-") }
    private val row = FrameLayout(context)
    private val cards = mutableListOf<ArchiveCard>()
    private val scroll = HorizontalScrollView(context).apply {
        id = R.id.archive_scroll
        tag = "archive.scroll"
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }
    private val fade = object : View(context) {
        private val fill = Paint()
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            fill.shader = LinearGradient(0f,0f,w.toFloat(),0f,Color.TRANSPARENT,Color.WHITE,Shader.TileMode.CLAMP)
        }
        override fun onDraw(canvas: Canvas) {
            // Reveal the last card completely once the user reaches the end.
            if (scroll.canScrollHorizontally(1)) canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),fill)
        }
    }.apply { tag="96:21579"; importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }

    init {
        id = R.id.archive_root
        tag = "96:21525"
        setWillNotDraw(false)
        add(label("96:21546","여행 도감",44f,64f,49.6f,0xFF222222.toInt(),700).apply {
            isAccessibilityHeading=true
        },58f,49f,174.75f,64f)
        add(button("archive.back","‹ 뒤로가기",26f,72f,44.7f,Color.TRANSPARENT,
            0xFF222222.toInt(),action=onClose).apply {
            contentDescription="뒤로가기, N딩 크레딧 홈으로 돌아가기"
            tooltipText="홈으로 돌아가기"
        },1382f,45f,260f,72f)

        if (pending != null) {
            val panel = FrameLayout(context).apply {
                tag="96:21531"
                background=shape(Color.TRANSPARENT,18f,3f,0xFF999999.toInt())
            }
            add(panel,58f,141.80005f,1584f,138f)
            place(panel,label("96:21543","보던 크레딧이 있어요 · ${pending.name}",32f,46f,35.8f,
                0xFF222222.toInt(),700).apply { ellipsize=TextUtils.TruncateAt.END },
                31f,27f,542.56f,46f)
            val frame=pendingFrame.coerceIn(0,CREDIT_FRAME_COUNT-1)
            val scene=CreditScene.entries[frame].title
            place(panel,label("96:21541","$scene (장면 ${frame+1} / $CREDIT_FRAME_COUNT)",26f,37f,28.9f,
                0xFF666666.toInt()).apply { ellipsize=TextUtils.TruncateAt.END },31f,73.39f,542.06f,37f)
            place(panel,button("96:21537","처음부터",24f,35f,44.69f,Color.TRANSPARENT,
                0xFF222222.toInt(),3f) { onResume(0) },1022.42f,33.03f,150.33f,72f)
            place(panel,button("96:21535","마지막 장면만",24f,35f,44.69f,Color.TRANSPARENT,
                0xFF222222.toInt(),3f) { onResume(CREDIT_FRAME_COUNT-1) },1186.75f,33.03f,201.2f,72f)
            place(panel,button("96:21533","이어 보기",24f,35f,44.69f,0xFF333333.toInt(),
                Color.WHITE) { onResume(null) },1401.95f,33.03f,151.05f,72f)
        }

        val headingY=if(pending != null) 307.88013f else 141.80005f
        val cardsY=if(pending != null) 379.135f else 213.055f
        if (visibleTrips.isEmpty()) {
            add(label("archive.empty","아직 기록이 없어요",32f,46f,35.8f,0xFF222222.toInt(),700).apply {
                gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL
            },58f,510f,1584f,46f)
            add(label("archive.emptyNote","여행을 끝내면 기록이 여기에 남아요.",26f,37f,28.9f,
                0xFF666666.toInt()).apply { gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL },58f,580f,1584f,37f)
        } else {
            add(label("96:21528",selectedYear?.let { "${it}년 여행" } ?: "최근 여행",32f,46f,35.8f,
                0xFF222222.toInt(),700).apply { isAccessibilityHeading=true },58f,headingY,1584f,46f)
            visibleTrips.forEachIndexed { index, trip ->
                val card=ArchiveCard(trip,index,{ onOpen(trip) },{ rename(trip,onRename) }) { anchor ->
                    menu?.dismiss()
                    menu=PopupMenu(context,anchor).apply {
                        this.menu.add(0,1,0,"여행 보기")
                        this.menu.add(0,2,1,"이름 수정")
                        this.menu.add(0,3,2,"이 연도만 보기")
                        this.menu.add(0,4,3,"연말 결산 보기")
                        if(selectedYear != null) this.menu.add(0,5,4,"전체 여행 보기")
                        setOnMenuItemClickListener { item ->
                            when(item.itemId) {
                                1 -> onOpen(trip)
                                2 -> rename(trip,onRename)
                                3 -> onSelectYear(trip.createdOn.take(4).toIntOrNull())
                                4 -> { onSelectYear(trip.createdOn.take(4).toIntOrNull()); onReview() }
                                5 -> onSelectYear(null)
                            }
                            true
                        }
                        show()
                    }
                }
                cards+=card
                place(row,card,index*444f,0f,420f,630f)
            }
            add(scroll,58f,cardsY,1640f,630f)
            add(fade,1487.9502f,cardsY,210.0498f,630f)
            scroll.setOnScrollChangeListener { _,_,_,_,_ -> fade.invalidate() }
        }
        add(button("96:21529","여행 시작",30f,44f,59.25f,0xFF0064FF.toInt(),Color.WHITE,
            action=onStart),58f,1038f,1584f,96f)
    }

    override fun onMeasure(widthMeasureSpec: Int,heightMeasureSpec: Int) {
        val width=MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        val height=resolveSize((width*1184f/1700f).roundToInt(),heightMeasureSpec).coerceAtLeast(1)
        setMeasuredDimension(width,height)
        scale=min(width/1700f,height/1184f)
        surface.set((width-1700f*scale)/2f,(height-1184f*scale)/2f,
            (width+1700f*scale)/2f,(height+1184f*scale)/2f)
        placements.forEach {
            it.params.width=px(it.width); it.params.height=px(it.height)
            it.params.leftMargin=px(it.x); it.params.topMargin=px(it.y)
            if(it.view.layoutParams !== it.params) it.view.layoutParams=it.params
        }
        typography.forEach { it.view.setReferenceTypography(it.size,it.height,it.baseline,scale) }
        shapes.forEach {
            it.drawable.cornerRadius=it.radius*scale
            if(it.stroke>0f) it.drawable.setStroke(px(it.stroke).coerceAtLeast(1),it.color)
        }
        row.layoutParams.width=px((visibleTrips.size*444f-24f).coerceAtLeast(0f))
        row.layoutParams.height=px(630f)
        rootViews.forEach { it.view.measure(exact(px(it.width)),exact(px(it.height))) }
        border.strokeWidth=2f*scale // Independent border weights override strokeWeight=1.
        edge.set(surface); edge.inset(scale,scale)
    }

    override fun onLayout(changed: Boolean,left: Int,top: Int,right: Int,bottom: Int) {
        rootViews.forEach {
            val x=(surface.left+it.x*scale).roundToInt()
            val y=(surface.top+it.y*scale).roundToInt()
            it.view.layout(x,y,x+it.view.measuredWidth,y+it.view.measuredHeight)
        }
        fade.invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRoundRect(surface,24f*scale,24f*scale,paper)
        canvas.drawRoundRect(edge,23f*scale,23f*scale,border)
    }

    override fun onDetachedFromWindow() {
        menu?.dismiss(); menu=null
        dialog?.dismiss(); dialog=null
        super.onDetachedFromWindow()
    }

    private fun rename(trip: Trip,save: (Trip,String)->Unit) {
        val input=EditText(context).apply {
            tag="archive.renameInput"
            setSingleLine(); filters=arrayOf(InputFilter.LengthFilter(50))
            setText(trip.name); selectAll()
        }
        val editor=AlertDialog.Builder(context).setTitle("여행 이름 수정").setView(input)
            .setNegativeButton("취소",null).setPositiveButton("저장",null).create()
        dialog=editor
        editor.setOnDismissListener { dialog=null }
        editor.setOnShowListener {
            editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name=input.text.toString().trim()
                if(name.isEmpty()) input.error="여행 이름을 입력해 주세요."
                else { editor.dismiss(); save(trip,name) }
            }
        }
        editor.show()
    }

    private inner class ArchiveCard(trip: Trip,index: Int,open: ()->Unit,edit: ()->Unit,
        more: (View)->Unit) : ViewGroup(context) {
        private val bounds=RectF()
        private val sourceCrop=Rect()
        private val clip=Path()
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val gradient=Paint(Paint.ANTI_ALIAS_FLAG)
        private val cover=photos.getOrPut(COVERS[DemoContent.coverIndex(trip) ?: (index % COVERS.size)]) {
            val resource=COVERS[DemoContent.coverIndex(trip) ?: (index % COVERS.size)]
            val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            BitmapFactory.decodeResource(resources,resource,bounds)
            BitmapFactory.decodeResource(resources,resource,BitmapFactory.Options().apply {
                // Downsample only when the resulting crop still covers 420 x 630.
                inSampleSize=if(bounds.outWidth>=840 && bounds.outHeight>=1260) 2 else 1
            })
        }
        private val children=mutableListOf<Placement>()

        init {
            tag="archive.card.$index"
            setWillNotDraw(false)
            setLayerType(LAYER_TYPE_SOFTWARE,null)
            isClickable=true; isFocusable=true
            contentDescription="${trip.name}, ${trip.createdOn}, ${trip.mode.title}"
            setOnClickListener { open() }
            fun addChild(view: View,x: Float,y: Float,w: Float,h: Float) {
                val p=Placement(view,x,y,w,h)
                children+=p; addView(view)
            }
            addChild(label("archive.card.$index.title",trip.name,32f,46f,36.952f,Color.WHITE,700).apply {
                ellipsize=TextUtils.TruncateAt.END
            // A one-pixel guard accommodates the installed font's fractional advance.
            },19.67871f,497.54257f,126f,46f)
            val date=runCatching { LocalDate.parse(trip.createdOn).format(DATE_FORMAT) }.getOrDefault(trip.createdOn)
            addChild(label("archive.card.$index.date",date,24f,35f,27.964f,0xADFFFFFF.toInt()).apply {
                ellipsize=TextUtils.TruncateAt.END
            },19.67871f,544.54254f,380f,35f)
            addChild(AppCompatImageButton(context).apply {
                tag="archive.card.$index.more"
                setImageResource(R.drawable.archive_more)
                scaleType=ImageView.ScaleType.FIT_XY
                setBackgroundColor(Color.TRANSPARENT); setPadding(0,0,0,0)
                contentDescription="${trip.name} 메뉴"
                setOnClickListener { more(this) }
            },351.6787f,33.72998f,48f,48f)
            // The source's small '대충 제목 수정 아이콘' note describes this action.
            // Render a working edit control rather than shipping that design note.
            addChild(AppCompatImageButton(context).apply {
                tag="archive.card.$index.edit"
                setImageResource(android.R.drawable.ic_menu_edit)
                imageTintList=ColorStateList.valueOf(Color.WHITE)
                setBackgroundColor(Color.TRANSPARENT); setPadding(0,0,0,0)
                contentDescription="${trip.name} 이름 수정"
                setOnClickListener { edit() }
            },155.50879f,503.04257f,35f,35f)
        }

        override fun onMeasure(widthMeasureSpec: Int,heightMeasureSpec: Int) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),MeasureSpec.getSize(heightMeasureSpec))
            children.forEach { it.view.measure(exact(px(it.width)),exact(px(it.height))) }
        }

        override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) {
            bounds.set(0f,0f,w.toFloat(),h.toFloat())
            val cropWidth=min(cover.width.toFloat(),cover.height*420f/630f)
            val cropHeight=min(cover.height.toFloat(),cover.width*630f/420f)
            sourceCrop.set(((cover.width-cropWidth)/2).roundToInt(),((cover.height-cropHeight)/2).roundToInt(),
                ((cover.width+cropWidth)/2).roundToInt(),((cover.height+cropHeight)/2).roundToInt())
            clip.rewind(); clip.addRoundRect(bounds,30f*scale,30f*scale,Path.Direction.CW)
            gradient.shader=LinearGradient(0f,317.38452f*scale,0f,630f*scale,
                Color.TRANSPARENT,Color.BLACK,Shader.TileMode.CLAMP)
            gradient.maskFilter=BlurMaskFilter((2f*scale).coerceAtLeast(.01f),BlurMaskFilter.Blur.NORMAL)
        }

        override fun onLayout(changed: Boolean,left: Int,top: Int,right: Int,bottom: Int) {
            children.forEach {
                val x=px(it.x); val y=px(it.y)
                it.view.layout(x,y,x+it.view.measuredWidth,y+it.view.measuredHeight)
            }
        }

        override fun onDraw(canvas: Canvas) {
            canvas.withClip(clip) {
                drawBitmap(cover,sourceCrop,bounds,paint)
                drawRect(0f,317.38452f*scale,bounds.right,bounds.bottom,gradient)
            }
        }

        override fun dispatchDraw(canvas: Canvas) {
            canvas.withClip(clip) { super.dispatchDraw(this) }
        }
        override fun generateDefaultLayoutParams()=LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT)
    }

    private fun label(tag: String,copy: String,size: Float,height: Float,baseline: Float,color: Int,
        weight: Int=400)=ReferenceDesignLabel(context).apply {
        this.tag=tag; text=copy
        typeface=ResourcesCompat.getFont(context,if(weight==700)R.font.noto_sans_kr_bold else R.font.noto_sans_kr_regular)
        includeFontPadding=false; letterSpacing=0f; maxLines=1
        setTextColor(color); setPadding(0,0,0,0); gravity=Gravity.TOP or Gravity.START
        typography+=Typography(this,size,height,baseline)
    }

    private fun button(tag: String,copy: String,size: Float,height: Float,baseline: Float,fill: Int,
        color: Int,stroke: Float=0f,action: ()->Unit)=ReferenceDesignButton(context).apply {
        this.tag=tag; text=copy; contentDescription=copy; isAllCaps=false
        typeface=ResourcesCompat.getFont(context,R.font.noto_sans_kr_bold)
        includeFontPadding=false; letterSpacing=0f; maxLines=1; gravity=Gravity.CENTER
        minWidth=0; minimumWidth=0; minHeight=0; minimumHeight=0
        insetTop=0; insetBottom=0; setPadding(0,0,0,0)
        elevation=0f; stateListAnimator=null; strokeWidth=0
        backgroundTintList=null; background=shape(fill,16f,stroke,0xFF888888.toInt())
        setTextColor(color); setOnClickListener { action() }
        typography+=Typography(this,size,height,baseline)
    }

    private fun shape(fill: Int,radius: Float,stroke: Float=0f,color: Int=Color.TRANSPARENT)=GradientDrawable().apply {
        setColor(fill); shapes+=Shape(this,radius,stroke,color)
    }
    private fun add(view: View,x: Float,y: Float,width: Float,height: Float) {
        val p=Placement(view,x,y,width,height); rootViews+=p
        addView(view)
    }
    private fun place(parent: FrameLayout,view: View,x: Float,y: Float,width: Float,height: Float) {
        val p=Placement(view,x,y,width,height); placements+=p
        parent.addView(view,p.params)
    }
    override fun generateDefaultLayoutParams()=LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT)
    private fun px(value: Float)=(value*scale).roundToInt()
    private fun exact(value: Int)=MeasureSpec.makeMeasureSpec(value,MeasureSpec.EXACTLY)
    private companion object {
        val COVERS=intArrayOf(R.drawable.archive_gangneung,R.drawable.archive_gyeongju,R.drawable.archive_busan,R.drawable.archive_sokcho)
        val DATE_FORMAT: DateTimeFormatter=DateTimeFormatter.ofPattern("yyyy년 M월 d일",Locale.KOREAN)
    }
}
