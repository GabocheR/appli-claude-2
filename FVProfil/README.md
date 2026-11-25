# FV Profil — profil force-vitesse en squat jump chargé (Android)

Application basée sur Samozino 2008, Samozino 2012, Morin & Samozino 2016 et Jiménez-Reyes 2017.

## Obtenir l'APK avec GitHub
1. Créer un dépôt GitHub (privé ou public) et y envoyer **tout le contenu de ce dossier** (y compris `.github/`).
2. Onglet **Actions** → workflow « Build APK » (se lance au push ; sinon « Run workflow »).
3. En fin d'exécution (~5-8 min), télécharger l'artefact **FVProfil-apk** → `app-debug.apk`.
4. Sur le téléphone : autoriser l'installation d'applications inconnues, puis installer l'APK.

## Utilisation
Athlètes → Nouveau test → format vidéo (paysage ou portrait) → ajouter les charges une par une → pour chaque charge : Filmer (ou Importer) puis marquer image par image (gros bouton rouge) → Calculer le profil.
Menu ☰ : protocole détaillé, glossaire, références.

## Ce qui est calculé
Par saut : tA, h = g·tA²/8, vTO, F, v, P moyennes (Samozino 2008), tPO, travail.
Profil : régression F-v (F0, V0, Pmax = F0·V0/4, Sfv, R²), Sfv optimal (Samozino 2012, éq. 6 résolue numériquement),
profil en % de l'optimal, catégorie (seuils 60/90/110/140 %), gain potentiel de hauteur, charge optimale de puissance.
R² < 0,98 : la charge incohérente est désignée (à refaire). Entre 2 essais d'une même charge, celui qui rend la droite F-v la plus cohérente est retenu.
Recommandations : tableaux 1 et 2 de Jiménez-Reyes 2017 (9 semaines, 2 séances/sem, 18 séries/sem).

## Points à connaître
- Les formats 120/240 ips dépendent du téléphone (modes « ralenti » haute vitesse Camera2) ; la liste proposée vient de l'appareil.
- Vidéos importées : si la cadence détectée est fausse (ralenti stocké à 30 ips), la corriger via « Modifier ips ».
- Le décodage image par image et l'analyse automatique peuvent prendre de quelques secondes à ~1 minute selon le téléphone.
- Les données sont stockées localement (`data.json` + vidéos dans le stockage privé de l'app). Pas d'export pour l'instant.
