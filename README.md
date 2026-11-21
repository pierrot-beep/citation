# Radar Alerte — application Android

Application Android (Kotlin) qui affiche votre position en temps réel sur une carte
OpenStreetMap et vous avertit quand un **radar fixe** est à moins de **400 m**.

## Fonctionnalités
- Carte OpenStreetMap (bibliothèque osmdroid, aucune clé d'API nécessaire) qui suit votre position GPS.
- Radars fixes affichés en points rouges (toucher un point affiche sa description).
- Sous 400 m du radar le plus proche :
  - bandeau rouge en haut de l'écran avec la distance mise à jour en continu ;
  - cercle de 400 m dessiné autour du radar ;
  - annonce vocale « Attention, radar à N mètres » (une fois par radar ; ré-annoncé après s'en être éloigné de plus de 600 m).
- Écran maintenu allumé pendant l'utilisation.

## Données radars
Téléchargées depuis data.gouv.fr au premier lancement, puis gardées sur le téléphone
(mise à jour automatique si le fichier a plus de 30 jours, ou via le bouton **MAJ radars**) :
1. [Liste des radars fixes en France](https://www.data.gouv.fr/fr/datasets/liste-des-radars-fixes-en-france/) (source principale) ;
2. [Radars automatiques](https://www.data.gouv.fr/fr/datasets/radars-automatiques/) (secours, plus ancien).

L'application prend le fichier CSV le plus récent du jeu de données et détecte elle-même
le séparateur et les colonnes latitude/longitude.

## Limites
- Radars **fixes** de France uniquement : pas de radars mobiles, ni de radars de chantier.
- L'alerte se base sur la distance à vol d'oiseau, sans tenir compte du sens de circulation
  ni de la route empruntée : un radar sur une route parallèle ou dans l'autre sens déclenche aussi l'alerte.
- L'alerte fonctionne quand l'application est **au premier plan** (pas de surveillance en arrière-plan).
- Une connexion Internet est requise pour le premier téléchargement des radars et pour charger les fonds de carte.

## Compiler l'APK (GitHub Actions)
1. Ouvrez l'onglet **Actions** du dépôt, choisissez **Construire l'APK Android**, puis **Run workflow**
   (le workflow se lance aussi à chaque push sur `main` ou sur une branche `claude/...`).
2. À la fin, téléchargez l'artefact `RadarAlerte-APK` : il contient `app-debug.apk`.
3. Transférez l'APK sur le téléphone et ouvrez-le pour l'installer (autorisez l'installation depuis cette source si Android le demande).
4. Au premier lancement, acceptez la permission de localisation.
