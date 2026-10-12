# Citation du jour — widget Android

Widget natif Android écrit en Kotlin, avec 100 citations françaises intégrées.

## Fonctionnalités
- 100 citations stockées dans le code, utilisables hors ligne.
- Citation stable pour la journée, commune à tous les widgets.
- Les citations défilent sur un cycle de 100 jours.
- Widget redimensionnable avec fond dégradé.

## Installation
1. Décompressez le ZIP.
2. Ouvrez le dossier `CitationDuJour` dans Android Studio.
3. Synchronisez Gradle (JDK 17 recommandé) et lancez l'application sur un appareil ou un émulateur.
4. Sur l'écran d'accueil Android, faites un appui long → **Widgets** → **Citation du jour**.

## Remarque
Android et les lanceurs peuvent différer dans la fréquence de rafraîchissement. Le contenu est recalculé selon la date locale lors de chaque mise à jour du widget. Pour une actualisation quotidienne garantie, on peut ajouter un travail périodique WorkManager, soumis aux règles d'économie d'énergie d'Android.

## Compiler sans Android Studio (via GitHub)
1. Créez un compte sur https://github.com si nécessaire.
2. Créez un dépôt vide, par exemple `CitationDuJour`.
3. Téléversez les fichiers et dossiers de ce projet dans le dépôt (le dossier `.github/workflows` doit aussi être présent à la racine).
4. Ouvrez l'onglet **Actions**, choisissez **Construire l'APK Android**, puis **Run workflow** si le lancement automatique n'a pas démarré.
5. Une fois le travail terminé, ouvrez le résultat et téléchargez l'artefact `CitationDuJour-APK`. Il contient `app-debug.apk`.
6. Transférez l'APK sur le téléphone Android et ouvrez-le pour l'installer. Android peut demander d'autoriser l'installation depuis cette source.

Le workflow compile le projet dans les serveurs GitHub : aucun Android Studio ni SDK Android local n'est nécessaire. Le projet doit être envoyé dans un dépôt GitHub, et la compilation doit réussir avant que l'APK soit disponible.
