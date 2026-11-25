package com.fvprofile.data

import androidx.compose.runtime.mutableStateListOf
import com.fvprofile.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Stockage local JSON (athlètes + séances). Tout reste sur le téléphone. */
class Store(private val file: File) {
    val athletes = mutableStateListOf<Athlete>()
    val sessions = mutableStateListOf<Session>()

    private fun JSONObject.dOrNull(k: String): Double? = if (has(k) && !isNull(k)) getDouble(k) else null
    private fun JSONObject.iOrNull(k: String): Int? = if (has(k) && !isNull(k)) getInt(k) else null

    fun load() {
        if (!file.exists()) return
        try {
            val root = JSONObject(file.readText())
            val a = root.optJSONArray("athletes") ?: JSONArray()
            for (i in 0 until a.length()) a.getJSONObject(i).let {
                athletes.add(Athlete(it.getString("id"), it.getString("name"), it.optString("sex"), it.optString("birth"), it.optString("sport"),
                    it.getDouble("heightCm"), it.getDouble("massKg"), it.getDouble("leg"), it.getDouble("startH"), it.optString("notes"),
                    it.optJSONArray("weights")?.let { w -> (0 until w.length()).map { k -> WeightEntry(w.getJSONObject(k).getLong("d"), w.getJSONObject(k).getDouble("kg")) } }
                        ?: listOf(WeightEntry(System.currentTimeMillis(), it.getDouble("massKg")))))
            }
            val s = root.optJSONArray("sessions") ?: JSONArray()
            for (i in 0 until s.length()) s.getJSONObject(i).let { o ->
                val sets = o.getJSONArray("sets")
                sessions.add(Session(o.getString("id"), o.getString("athleteId"), o.getLong("date"), o.getDouble("massKg"), o.getDouble("hPO"),
                    o.optBoolean("auto"), o.optString("cam"),
                    (0 until sets.length()).map { j ->
                        val st = sets.getJSONObject(j); val tr = st.getJSONArray("trials")
                        LoadSet(st.getString("id"), st.getDouble("load"), if (st.isNull("video")) null else st.getString("video"), st.optDouble("fps", 0.0),
                            (0 until tr.length()).map { k ->
                                val t = tr.getJSONObject(k)
                                TrialData(t.iOrNull("start"), t.iOrNull("to"), t.iOrNull("ld"), t.dOrNull("knee"), t.dOrNull("hip"), t.optInt("src", 0))
                            }, if (st.has("video2") && !st.isNull("video2")) st.getString("video2") else null, st.optDouble("fps2", 0.0))
                    }, o.optString("name")))
            }
        } catch (e: Exception) { /* fichier corrompu : on repart à vide sans écraser */ file.copyTo(File(file.path + ".bak"), true) }
    }

    fun save() {
        val root = JSONObject()
        root.put("athletes", JSONArray().also { arr -> athletes.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("sex", it.sex).put("birth", it.birth).put("sport", it.sport)
                .put("heightCm", it.heightCm).put("massKg", it.massKg).put("leg", it.legLengthM).put("startH", it.startHeightM).put("notes", it.notes)
                .put("weights", JSONArray().also { wa -> it.weights.forEach { w -> wa.put(JSONObject().put("d", w.date).put("kg", w.kg)) } })) } })
        root.put("sessions", JSONArray().also { arr -> sessions.forEach { s ->
            arr.put(JSONObject().put("id", s.id).put("athleteId", s.athleteId).put("date", s.date).put("massKg", s.massKg).put("hPO", s.hPO)
                .put("auto", s.auto).put("cam", s.cameraLabel).put("name", s.name)
                .put("sets", JSONArray().also { sa -> s.sets.forEach { st ->
                    sa.put(JSONObject().put("id", st.id).put("load", st.loadKg).put("video", st.videoPath ?: JSONObject.NULL).put("fps", st.fps)
                        .put("video2", st.video2Path ?: JSONObject.NULL).put("fps2", st.fps2)
                        .put("trials", JSONArray().also { ta -> st.trials.forEach { t ->
                            ta.put(JSONObject().put("start", t.startFrame ?: JSONObject.NULL).put("to", t.takeoff ?: JSONObject.NULL)
                                .put("ld", t.landing ?: JSONObject.NULL).put("knee", t.kneeDeg ?: JSONObject.NULL).put("hip", t.hipDeg ?: JSONObject.NULL).put("src", t.src)) } }))
                } })) } })
        val tmp = File(file.path + ".tmp"); tmp.writeText(root.toString()); tmp.renameTo(file)
    }
}
