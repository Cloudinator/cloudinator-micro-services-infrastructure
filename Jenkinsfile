@Library('cloudinator-microservices') _

pipeline {
    agent any
    parameters {
        string(name: 'SERVICES', defaultValue: 'eureka,config-server,service1,service2,service3,gateway', description: 'Comma-separated list of services to deploy in order')
    }
    stages {
        stage('Deploy Microservices') {
            steps {
                script {
                    def services = params.SERVICES.split(',')
                    for (service in services) {
                        stage("Deploy ${service}") {
                            build job: "${service}-pipeline", wait: true
                        }
                    }
                }
            }
        }
    }
    post {
        always {
            echo 'Deployment of all services completed'
        }
    }
}

