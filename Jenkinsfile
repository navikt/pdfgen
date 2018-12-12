#!/usr/bin/env groovy

pipeline {
    agent any

    tools {
        jdk "openjdk11"
    }

    environment {
        ZONE = 'fss'
        APPLICATION_NAME = 'pdf-gen'
        DOCKER_SLUG = 'integrasjon'
        GITHUB_NAME = 'pdfgen'
        KUBECONFIG="kubeconfig-teamsykefravr"

        FASIT_ENVIRONMENT = 'q1'
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
        stage('create uber jar') {
            steps {
                sh './gradlew shadowJar'
            }
        }
        stage('push docker image') {
            steps {
                dockerUtils action: 'createPushImage'
            }
        }
        stage('deploy to preprod') {
            parallel {
                stage("deploy to preprod FSS") {
                    steps {
                        deployApp action: 'kubectlDeploy', cluster: 'preprod-fss'
                    }
                }
                stage("deploy to preprod SBS") {
                    steps {
                        deployApp action: 'kubectlDeploy', cluster: 'preprod-sbs'
                    }
                }
            }
        }
        stage('deploy to production') {
            when { environment name: 'DEPLOY_TO', value: 'production' }
            parallel {
                stage("deploy to prod FSS") {
                    steps {
                        deployApp action: 'kubectlDeploy', cluster: 'prod-fss'
                    }
                }
                stage("deploy to prod SBS") {
                    steps {
                        deployApp action: 'kubectlDeploy', cluster: 'prod-sbs'
                    }
                }
            }
        }
        stage('tag git release') {
            when { environment name: "DEPLOY_TO", value: 'production' }
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
