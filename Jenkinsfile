pipeline {
    agent { label 'docker-compose' }

    options {
        skipDefaultCheckout(true)
        timestamps()
        disableConcurrentBuilds()
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    def jobKey = Integer.toHexString(env.JOB_NAME.hashCode())
                    def buildKey = env.BUILD_NUMBER.toLowerCase().replaceAll(/[^a-z0-9_-]/, '-')
                    env.COMPOSE_PROJECT_NAME = "atlasdesk-ci-${jobKey}-${buildKey}"
                }
            }
        }

        stage('Validate Compose') {
            steps {
                sh 'docker compose --profile e2e config --quiet'
            }
        }

        stage('Backend build and compile') {
            steps {
                sh 'docker compose --profile e2e build e2e-backend'
                sh 'docker compose --profile e2e run --rm --no-deps e2e-backend mvn -B -ntp -DskipTests compile'
            }
        }

        stage('Unit and integration tests') {
            steps {
                sh 'mkdir -p backend/.ci-reports/surefire-reports'
                sh 'rm -f backend/.ci-reports/surefire-reports/*.xml'
                sh '''
                    docker compose --profile e2e run --rm e2e-backend sh -c '
                        mvn -B -ntp test
                        result=$?
                        mkdir -p /workspace/backend/.ci-reports/surefire-reports
                        cp -R /workspace/backend/target/surefire-reports/. /workspace/backend/.ci-reports/surefire-reports/
                        exit $result
                    '
                '''
            }
        }

        stage('Frontend lint, typecheck and build') {
            steps {
                sh 'docker compose --profile e2e run --rm --no-deps e2e-frontend sh -c "npm ci --prefer-offline && npm run lint && npm run typecheck && npm run build"'
            }
        }

        stage('Cypress typecheck') {
            steps {
                sh 'docker compose --profile e2e run --rm --entrypoint sh e2e -c "npm ci --prefer-offline && npm run typecheck"'
            }
        }

        stage('Start isolated services') {
            steps {
                sh 'docker compose --profile e2e up -d --wait e2e-db e2e-backend e2e-frontend'
            }
        }

        stage('Cypress E2E') {
            steps {
                sh 'docker compose --profile e2e run --rm e2e'
            }
        }

        stage('Collect reports') {
            steps {
                archiveArtifacts artifacts: 'backend/.ci-reports/surefire-reports/**/*.xml,docs/screenshots/**/*.png,tests/e2e/cypress/screenshots/**/*,tests/e2e/cypress/videos/**/*', allowEmptyArchive: true
            }
        }

        stage('Cleanup') {
            steps {
                sh 'docker compose --profile e2e down --remove-orphans'
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: 'backend/.ci-reports/surefire-reports/**/*.xml,docs/screenshots/**/*.png,tests/e2e/cypress/screenshots/**/*,tests/e2e/cypress/videos/**/*', allowEmptyArchive: true
            sh 'docker compose --profile e2e down --remove-orphans'
        }
    }
}
