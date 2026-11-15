package com.example.pleos_endingcredit

/** Short-lived display data only. Provider names never enter trip JSON or preferences. */
internal object PlaceDisplayNames {
    private data class Entry(val name:String,val address:String,val roadAddress:String,val expires:Long)
    private val values=linkedMapOf<String,Entry>()
    @Synchronized fun remember(candidates:List<PlaceCandidate>){
        val now=System.currentTimeMillis();values.entries.removeAll{it.value.expires<=now}
        candidates.forEach{values[it.id]=Entry(it.name,it.address,it.roadAddress,now+20*60_000L)}
        while(values.size>256)values.remove(values.keys.first())
    }
    @Synchronized fun get(id:String?):String? = entry(id)?.name
    @Synchronized fun getAddress(id:String?):String? = entry(id)?.address
    @Synchronized fun getRoadAddress(id:String?):String? = entry(id)?.roadAddress
    private fun entry(id:String?):Entry? {
        if(id==null)return null
        val item=values[id]?:return null
        if(item.expires<=System.currentTimeMillis()){values.remove(id);return null}
        return item
    }
    fun display(stop:RecordedStop)=stop.userLabel?:get(stop.selectedPlaceId)?:if(stop.selectedPlaceId!=null)"확인한 장소" else "확인 필요"
}
