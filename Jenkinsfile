#!/usr/bin/env groovy

pipeline {
    agent any

    environment {
        ZONE = 'fss'
        APPLICATION_NAME = 'pdf-gen'
        DOCKER_SLUG = 'integrasjon'
        FASIT_ENVIRONMENT = 'q1'
        GITHUB_NAME = 'pdfgen'
    }

    stages {
        stage('initialize') {
            environment { APPLICATION_NAME = "${env.GITHUB_NAME}" }
            steps {
                init action: 'gradle'
            }
        }
        stage('build') {
            steps {
                sh './gradlew build -x test'
            }
        }
        stage('run tests (unit & intergration)') {
            steps {
                sh './gradlew test'
                slackStatus status: 'passed'
            }
        }
        stage('extract application files') {
            steps {
                sh './gradlew installDist'
            }
        }
        stage('push docker image') {
            steps {
                dockerUtils action: 'createPushImage'
            }
        }
        stage('validate & upload nais.yaml to nexus m2internal') {
            steps {
                nais action: 'validate'
                nais action: 'upload'
            }
        }
        stage('deploy to preprod') {
            parallel {
                stage("deploy to preprod FSS") {
                    steps {
                        deployApp action: 'jiraPreprod', zone: 'fss'
                    }
                }
                stage("deploy to preprod SBS") {
                    steps {
                        deployApp action: 'jiraPreprod', zone: 'sbs'
                    }
                }
            }
        }
        stage('deploy to production') {
            when { environment name: 'DEPLOY_TO', value: 'production' }
            parallel {
                stage("deploy to prod FSS") {
                    steps {
                        deployApp action: 'jiraProd', zone: 'fss'
                    }
                }
                stage("deploy to prod SBS") {
                    steps {
                        deployApp action: 'jiraProd', zone: 'sbs'
                    }
                }
            }
            steps {
                githubStatus action: 'tagRelease', applicationName: "${env.GITHUB_NAME}"
            }
        }
    }
    post {
        always {
            postProcess action: 'always', applicationName: "${env.GITHUB_NAME}"
            junit '**/build/test-results/test/*.xml'
            archiveArtifacts artifacts: 'build/reports/rules.csv', allowEmptyArchive: true
            archiveArtifacts artifacts: '**/build/libs/*', allowEmptyArchive: true
            archiveArtifacts artifacts: '**/build/install/*', allowEmptyArchive: true
        }
        success {
            postProcess action: 'success', applicationName: "${env.GITHUB_NAME}"
        }
        failure {
            postProcess action: 'failure', applicationName: "${env.GITHUB_NAME}"
        }
    }
}
