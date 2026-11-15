package com.example.pleos_endingcredit

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.util.Locale

/** Explicit microphone action, on-device only; no server credentials or audio files. */
internal class RecordingSpeechInput(private val context:Context,private val result:(String)->Unit,
    private val prompt:String="가게 이름을 말해 주세요") {
    private val handler=Handler(Looper.getMainLooper());private var active=false
    private var client:SpeechToText?=null
    private val listener=object:ResultListener {
        override fun onReady()=Unit
        override fun onStartedRecognition(){handler.post{if(active)Toast.makeText(context,prompt,Toast.LENGTH_SHORT).show()}}
        override fun onUpdated(stt:String,completed:Boolean){handler.post{if(active&&completed&&stt.isNotBlank()){result(stt);close()}}}
        override fun onUpdatedEpdData(on:Long,off:Long)=Unit
        override fun onUpdatedRms(rms:Double)=Unit
        override fun onEndedRecognition(){handler.post{close()}}
        override fun onError(errorType:ErrorType){handler.post{fail()}}
    }
    fun start(){
        active=true
        try {
            val speech=SpeechToText(context.applicationContext,Mode.ON_DEVICE);client=speech
            speech.initialize(object:OnInitializeListener {
                override fun onSuccess(){handler.post{if(active)try{speech.addListener(listener);speech.setLocale(Locale.KOREA);speech.request()}catch(_:Exception){fail()}}}
                override fun onFailure(errorType:ErrorType){handler.post{fail()}}
            })
            handler.postDelayed({if(active){Toast.makeText(context,"음성 입력을 마쳤어요. 직접 입력도 사용할 수 있어요",Toast.LENGTH_SHORT).show();close()}},20_000)
        }catch(_:Exception){fail()}
    }
    private fun fail(){if(active)Toast.makeText(context,"이 기기에서 음성 입력을 사용할 수 없어요. 키보드로 입력해 주세요",Toast.LENGTH_LONG).show();close()}
    fun close(){active=false;handler.removeCallbacksAndMessages(null);client?.let{runCatching{it.stop()};runCatching{it.removeListener(listener)};runCatching{it.release()}};client=null}
}
