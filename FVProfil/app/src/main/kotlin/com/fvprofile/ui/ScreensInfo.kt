@file:OptIn(ExperimentalMaterial3Api::class)

package com.fvprofile

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fvprofile.ui.*

@Composable
fun ProtocolScreen(vm: AppVM, onMenu: () -> Unit) = PageV(vm, "Protocole du test", onMenu) {
    Body("Squat jump (sans contre-mouvement) réalisé avec plusieurs charges additionnelles ; la hauteur est mesurée par la vidéo. Dépliez chaque étape.")
    Expand("1. Matériel", true) {
        Body("• Téléphone fixe (trépied) filmant en ralenti : 120 ips ou plus (±1 image = ±3,4 % sur la hauteur à 120 ips, ±1,7 % à 240 ips ; ±13 % à 30 ips).\n• Barre guidée (presse type Smith, comme dans l'étude) et disques ; poids de corps connu.\n• Mètre ruban et équerre/règle pour les mesures.")
        RefTag(Refs.J17); RefTag(Refs.MYJ)
    }
    Expand("2. Mesures préalables (une fois par athlète)") {
        Body("• Poids de corps (kg).\n• Longueur du membre inférieur étendu : repère osseux (crête iliaque) → pointe du pied, jambe tendue, pied en flexion plantaire maximale.\n• Hauteur de départ hS : même repère → sol, genoux fléchis à ≈ 90°.\n• Distance de poussée hPO = longueur du membre − hS.")
        RefTag(Refs.S08)
    }
    Expand("3. Échauffement") { Body("Échauffement standard de ≈ 5–10 min puis familiarisation avec le saut sans contre-mouvement (idéalement 2–3 jours avant le premier test). Testez toujours à la même heure de la journée pour suivre l'évolution."); RefTag(Refs.S08); RefTag(Refs.J17) }
    Expand("4. Charges") {
        Body("• 0 kg + au moins 5 charges additionnelles, régulièrement réparties de 0 kg jusqu'à la charge avec laquelle l'athlète saute environ 10 cm. (L'étude de 2017 utilisait 5 à 8 charges de 17 à 87 kg.)\n• Ordre aléatoire.\n• L'application propose 0, 25, 50, 75 et 100 % du poids de corps (arrondi à 5 kg) ; ajustez selon l'athlète.")
        RefTag(Refs.M16); RefTag(Refs.J17)
    }
    Expand("5. Exécution du saut") {
        Body("• Position de départ : genoux à ≈ 90°, tenue ≈ 2 s, tronc stable.\n• Bras sur les hanches (sans charge) ou sur la barre (avec charge), position identique pendant tout le mouvement.\n• Poussée la plus rapide possible et saut maximal, sans contre-mouvement (interdit et vérifié).\n• Réception jambes tendues et pied en flexion plantaire (même position qu'au décollage).\n• Essai invalide (contre-mouvement, flexion en vol, réception fléchie) : à refaire.")
        RefTag(Refs.S08); RefTag(Refs.J17)
    }
    Expand("6. Essais et récupération") { Body("• 2 essais valides par charge, filmés dans la même vidéo.\n• ≈ 2 min de repos entre les 2 essais, 4–5 min entre les charges.\n• L'application retient, pour chaque charge, l'essai le plus cohérent avec la droite force-vitesse."); RefTag(Refs.J17) }
    Expand("7. Filmer (conseils pratiques)") {
        Body("• Téléphone à l'horizontale (paysage), fixe, à hauteur des hanches, perpendiculaire au plan de saut (vue de profil), à 3–4 m.\n• Corps entier, barre et sol visibles tout au long du saut, sans zoom ; bon éclairage (le ralenti en demande beaucoup) et fond contrasté.\n• Pour l'analyse automatique : vêtements ajustés, une seule personne dans le champ.")
    }
    Expand("8. Calculs et contrôle qualité") {
        Body("• hauteur h = g·tA²/8 (tA = temps de vol image par image) ; F = m·g·(h/hPO + 1) ; v = √(g·h/2) ; P = F·v.\n• Régression linéaire F-v (≥ 3 charges, 5 recommandées) → F0, V0, Pmax = F0·V0/4, pente Sfv.\n• Profil comparé au profil optimal (Sfv_opt) pour un Pmax et un hPO donnés.\n• Validité : R² ≥ 0,98 ; sinon l'application désigne la charge incohérente à refaire.")
        RefTag(Refs.S08); RefTag(Refs.S12)
    }
    Expand("9. Fréquence des tests") { Body("Refaire le test régulièrement (ex. mensuellement) permet de suivre Pmax et le déséquilibre F-v, et d'ajuster le programme. Après un cycle d'entraînement de 9 semaines, retester une semaine après."); RefTag(Refs.M16); RefTag(Refs.J17) }
    Spacer(Modifier.height(24.dp))
}

@Composable
fun GlossaryScreen(vm: AppVM, onMenu: () -> Unit) = PageV(vm, "Glossaire des variables", onMenu) {
    data class G(val n: String, val d: String)
    listOf(
        G("F0 (N/kg)", "Force maximale théorique des membres inférieurs (ordonnée à l'origine de la droite F-v), par kg de masse corporelle."),
        G("V0 (m/s)", "Vitesse maximale théorique d'extension des membres inférieurs (intersection avec l'axe des vitesses) ; très difficile à atteindre réellement."),
        G("Pmax (W/kg)", "Puissance maximale, F0 × V0 / 4 (sommet de la parabole puissance-vitesse)."),
        G("Sfv", "Pente de la droite F-v (−F0/V0). Plus elle est négative, plus le profil est orienté « force »."),
        G("Sfv opt", "Pente qui maximise la hauteur de saut pour une Pmax, une masse et une distance de poussée données."),
        G("Profil / optimal (%)", "Sfv / Sfv_opt × 100 (FVimb). 100 % = équilibré ; < 100 % = déficit de force ; > 100 % = déficit de vitesse."),
        G("Déséquilibre (%)", "|100 − profil/optimal| : ampleur de l'écart à l'optimal, quel que soit son sens."),
        G("R²", "Qualité de la régression F-v (objectif ≥ 0,98)."),
        G("hPO (m)", "Distance de poussée : extension des membres inférieurs entre la position de départ et le décollage."),
        G("tA / h", "Temps de vol et hauteur du saut (h = g·tA²/8)."),
        G("vTO", "Vitesse du centre de masse au décollage, √(2·g·h)."),
        G("F, v, P moyennes", "Force, vitesse et puissance moyennes pendant la poussée (une valeur par saut)."),
        G("tPO", "Durée de la poussée, hPO / v."),
        G("W (J)", "Travail total des membres inférieurs, m·g·(hPO + h)."),
        G("Charge optimale", "Charge totale pour laquelle la puissance est maximale (v = V0/2) ; elle vaut le poids de corps seulement pour un profil optimal."),
        G("Gain potentiel", "Gain de hauteur de saut prédit par le modèle si le profil devenait optimal à Pmax constante."),
        G("Angles genou/hanche", "Angles intérieurs en position de départ (180° = extension) ; contrôle de la standardisation du départ.")
    ).forEach { Card2 { H(it.n); Body(it.d) } }
    RefTag(Refs.M16); RefTag(Refs.S12); RefTag(Refs.S08)
    Spacer(Modifier.height(24.dp))
}

@Composable
fun ReferencesScreen(vm: AppVM, onMenu: () -> Unit) = PageV(vm, "Références scientifiques", onMenu) {
    Body("Les méthodes et seuils de l'application reposent sur ces travaux.")
    Refs.all.forEach { Card2 { H(it.short); Body(it.full, TextHi, 13); Body(it.note, size = 13) } }
    Card2 { H("Limites"); Body("Estimations de terrain : la précision dépend de la cadence vidéo et du marquage des images. L'analyse automatique est moins précise que le marquage manuel. Les recommandations d'entraînement s'appuient sur une étude pilote et n'ont pas valeur d'avis médical.") }
    Spacer(Modifier.height(24.dp))
}
