# WebGuardian - Bot de Surveillance de Sites Web

WebGuardian est un bot de surveillance de sites web développé en Java qui permet de monitorer la disponibilité et les performances de plusieurs sites web et d'envoyer des alertes en cas de problème.

## Fonctionnalités

- **Surveillance en temps réel** de multiples sites web (HTTP/HTTPS)
- **Vérification périodique** configurable (toutes les X minutes/secondes)
- **Détection de problèmes**:
  - Temps de réponse excessif
  - Codes d'erreur HTTP (4xx, 5xx)
  - Indisponibilité complète (timeout)
  - Changements de contenu critiques (texte spécifique disparu/apparu)
- **Alertes multicanaux**:
  - Messages Discord (dans des canaux spécifiques)
  - Emails
  - SMS (optionnel via Twilio)
- **Rapports de performance** quotidiens/hebdomadaires/mensuels

## Architecture

Le projet est construit selon les principes de la Clean Architecture:

- **Core**: Contient les entités et les cas d'utilisation du domaine
- **Infrastructure**: Implémente les interfaces du core pour interagir avec le monde extérieur (HTTP, base de données, etc.)
- **Application**: Point d'entrée de l'application et configuration

## Prérequis

- Java 17 ou supérieur
- Maven
- Un bot Discord avec les permissions nécessaires
- Un serveur SMTP pour les notifications par email (optionnel)

## Installation

1. Clonez ce dépôt:
   ```
   git clone https://github.com/mangnluis/ServBotERG.git
   cd webguardian
   ```

2. Compilez le projet avec Maven:
   ```
   mvn clean package
   ```

3. Copiez et configurez le fichier de configuration:
   ```
   cp config.properties.example config.properties
   nano config.properties
   ```

4. Exécutez l'application:
   ```
   java -jar target/webguardian-1.0-SNAPSHOT.jar
   ```

## Configuration

Le fichier `config.properties` contient tous les paramètres nécessaires:

- **Discord**: Token du bot, préfixe de commande, canaux autorisés, etc.
- **Email**: Configuration SMTP pour les notifications par email
- **Base de données**: Configuration de la base de données H2
- **Monitoring**: Paramètres de surveillance par défaut

## Commandes Discord

- `!monitor add [url] [options]` - Ajoute un site à surveiller
  - Options: `--name=nom --interval=min --timeout=sec --retries=n --content-check=texte --ssl-check=true/false`
- `!monitor remove [url]` - Retire un site de la surveillance
- `!monitor list` - Liste tous les sites surveillés
- `!monitor status [url]` - Vérifie immédiatement l'état d'un site
- `!monitor config [url] [options]` - Configure les paramètres d'un site
  - Options: `--name=nom --interval=min --timeout=sec --retries=n --content-check=texte --ssl-check=true/false --maintenance=true/false`

### Commandes de rapport

- `!rapport quotidien` - Génère un rapport quotidien
- `!rapport hebdomadaire` - Génère un rapport hebdomadaire
- `!rapport mensuel` - Génère un rapport mensuel
- `!rapport site [id] [jours]` - Génère un rapport pour un site spécifique
- `!rapport custom [date-début] [date-fin]` - Génère un rapport personnalisé

## Développement

### Structure du projet

```
webguardian/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── webguardian/
│   │   │           ├── core/                # Domaine métier
│   │   │           │   ├── entities/        # Entités du domaine
│   │   │           │   ├── usecases/        # Cas d'utilisation
│   │   │           │   └── ports/           # Interfaces pour l'infrastructure
│   │   │           ├── infrastructure/      # Implémentations techniques
│   │   │           │   ├── web/             # HTTP client
│   │   │           │   ├── persistence/     # Base de données
│   │   │           │   ├── notifications/   # Services de notification
│   │   │           │   └── scheduling/      # Planification des tâches
│   │   │           ├── application/         # Application et configuration
│   │   │           └── common/              # Utilitaires communs
│   │   └── resources/
│   └── test/
│       └── java/
└── pom.xml
```

### Technologies utilisées

- **OkHttp3**: Client HTTP
- **JDA**: API Discord pour Java
- **Hibernate**: ORM pour la persistance
- **H2**: Base de données embarquée
- **Quartz**: Planification des tâches
- **Jakarta Mail**: Envoi d'emails
- **SLF4J & Logback**: Logging
- **Lombok**: Réduction du code boilerplate

## Licence

Ce projet est sous licence MIT. Voir le fichier LICENSE pour plus de détails.

## Contribution

Les contributions sont les bienvenues! N'hésitez pas à ouvrir une issue ou à soumettre une pull request.
