package com.fvprofile

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.fvprofile.core.*
import com.fvprofile.data.Store
import java.io.File

sealed class Screen {
    object Athletes : Screen()
    object Protocol : Screen()
    object Glossary : Screen()
    object References : Screen()
    data class AthleteEdit(val id: String?) : Screen()
    data class AthleteDetail(val id: String) : Screen()
    data class Setup(val athleteId: String) : Screen()
    data class Hub(val sessionId: String) : Screen()
    data class Record(val sessionId: String, val setId: String, val videoIdx: Int) : Screen()
    data class Analysis(val sessionId: String, val setId: String, val videoIdx: Int) : Screen()
    data class Results(val sessionId: String) : Screen()
    data class Reco(val sessionId: String) : Screen()
}

class AppVM(app: Application) : AndroidViewModel(app) {
    val store = Store(File(app.filesDir, "data.json")).also { it.load() }
    val stack = mutableStateListOf<Screen>(Screen.Athletes)
    private val prefs = app.getSharedPreferences("fv", 0)
    var camKey by mutableStateOf(prefs.getString("cam", "") ?: "")
        private set
    fun setCam(k: String) { camKey = k; prefs.edit().putString("cam", k).apply() }

    var portrait by mutableStateOf(prefs.getBoolean("portrait", false))
        private set
    fun setPortrait(b: Boolean) { portrait = b; prefs.edit().putBoolean("portrait", b).apply() }
    var exposure by mutableIntStateOf(prefs.getInt("exp", 0))
        private set
    fun setExposure(v: Int) { exposure = v; prefs.edit().putInt("exp", v).apply() }

    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.size - 1) }
    fun replace(s: Screen) { stack[stack.size - 1] = s }
    fun goRoot(s: Screen) { stack.clear(); stack.add(s) }

    fun athlete(id: String) = store.athletes.firstOrNull { it.id == id }
    fun session(id: String) = store.sessions.firstOrNull { it.id == id }
    fun sessionsOf(athleteId: String) = store.sessions.filter { it.athleteId == athleteId }.sortedBy { it.date }

    fun saveAthlete(a: Athlete) {
        val i = store.athletes.indexOfFirst { it.id == a.id }
        if (i >= 0) store.athletes[i] = a else store.athletes.add(a)
        store.save()
    }
    /** Ajoute une pesée à l'historique du poids ; le poids courant de l'athlète = pesée la plus récente. */
    fun addWeight(athleteId: String, kg: Double, date: Long = System.currentTimeMillis()) {
        val a = athlete(athleteId) ?: return
        val list = (a.weights + WeightEntry(date, kg)).sortedBy { it.date }
        saveAthlete(a.copy(weights = list, massKg = list.last().kg))
    }
    fun deleteWeight(athleteId: String, date: Long) {
        val a = athlete(athleteId) ?: return
        val list = a.weights.filter { it.date != date }
        if (list.isEmpty()) return
        saveAthlete(a.copy(weights = list, massKg = list.last().kg))
    }
    fun renameSession(id: String, name: String) { session(id)?.let { saveSession(it.copy(name = name.trim())) } }
    fun deleteAthlete(id: String) {
        store.sessions.filter { it.athleteId == id }.forEach { deleteSession(it.id) }
        store.athletes.removeAll { it.id == id }; store.save()
    }
    fun saveSession(s: Session) {
        val i = store.sessions.indexOfFirst { it.id == s.id }
        if (i >= 0) store.sessions[i] = s else store.sessions.add(s)
        store.save()
    }
    fun deleteSession(id: String) {
        session(id)?.sets?.forEach { deleteSetFiles(it) }
        store.sessions.removeAll { it.id == id }; store.save()
    }
    fun updateSet(sid: String, setId: String, f: (LoadSet) -> LoadSet) {
        val s = session(sid) ?: return
        saveSession(s.copy(sets = s.sets.map { if (it.id == setId) f(it) else it }))
    }
    /** Supprime les fichiers vidéo d'une séance (marqueurs, profil et résultats sont conservés). Renvoie l'espace libéré en octets. */
    fun deleteVideos(sid: String): Long {
        val s = session(sid) ?: return 0L
        var freed = 0L
        s.sets.forEach { st -> listOfNotNull(st.videoPath, st.video2Path).forEach { val f = File(it); freed += f.length(); f.delete() } }
        saveSession(s.copy(sets = s.sets.map { it.copy(videoPath = null, video2Path = null) }))
        return freed
    }
    fun videoBytes(sid: String): Long = session(sid)?.sets?.sumOf { st -> listOfNotNull(st.videoPath, st.video2Path).sumOf { File(it).length() } } ?: 0L
    fun deleteSetFiles(st: LoadSet) { listOfNotNull(st.videoPath, st.video2Path).forEach { File(it).delete() } }
    /** Associe une vidéo (filmée ou importée) à une charge. Vidéo 1 : réinitialise la charge. Vidéo 2 : concerne l'essai manquant. */
    fun attachVideo(sid: String, setId: String, videoIdx: Int, path: String, fps: Double) {
        val st = session(sid)?.sets?.firstOrNull { it.id == setId } ?: return
        if (videoIdx == 0) {
            deleteSetFiles(st)
            updateSet(sid, setId) { it.copy(videoPath = path, fps = fps, video2Path = null, fps2 = 0.0, trials = listOf(TrialData(), TrialData())) }
        } else {
            st.video2Path?.let { File(it).delete() }
            val miss = if (st.trials[0].tA(st.fpsFor(st.trials[0])) == null) 0 else 1
            updateSet(sid, setId) { it.copy(video2Path = path, fps2 = fps, trials = it.trials.mapIndexed { i, t -> if (i == miss) TrialData(src = 1) else t }) }
        }
    }
    /** Remet une charge à zéro (vidéos et marqueurs) pour la refaire. */
    fun resetSet(sid: String, setId: String) {
        session(sid)?.sets?.firstOrNull { it.id == setId }?.let { deleteSetFiles(it) }
        updateSet(sid, setId) { it.copy(videoPath = null, fps = 0.0, video2Path = null, fps2 = 0.0, trials = listOf(TrialData(), TrialData())) }
    }
    fun newId() = java.util.UUID.randomUUID().toString().take(8)
}
