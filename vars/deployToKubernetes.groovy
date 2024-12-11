// def call(String inventoryFile, String playbookFile, String serviceName, String image, 
//          String namespace, String filePath, String domainName, String email, String gitRepoUrl) {
    
//     def tmpDir = "tmp-${serviceName}-${UUID.randomUUID().toString()}"
    
//     try {
//         dir(tmpDir) {
//             git(url: gitRepoUrl, branch: 'main', credentialsId: 'git-credentials')
//         }
        
//         def projectInfo = detectProjectType(tmpDir)
//         if (!projectInfo) {
//             error "Failed to detect project type for repository: ${gitRepoUrl}"
//         }
        
//         def port = projectInfo.port ?: 8080
        
//         echo """
//         Project Detection Results for ${serviceName}:
//         -------------------------
//         Type: ${projectInfo.type}
//         Port: ${port}
//         Path: ${tmpDir}
//         """

//         validateParameters(inventoryFile, playbookFile, serviceName, image, namespace, filePath, domainName, email)

//         def extraVars = [
//             "SERVICE_NAME": serviceName,
//             "IMAGE": image,
//             "NAMESPACE": namespace,
//             "FILE_Path": filePath,
//             "DOMAIN_NAME": domainName,
//             "EMAIL": email,
//             "PORT": port
//         ]

//         // Add service-specific configurations
//         switch(serviceName) {
//             case 'eureka':
//                 extraVars.put("EUREKA_ROLE", "server")
//                 break
//             case 'config-server':
//                 extraVars.put("CONFIG_REPO", "https://github.com/your-org/config-repo.git")
//                 break
//             case 'gateway':
//                 extraVars.put("GATEWAY_ROUTES", "service1,service2,service3")
//                 break
//             default:
//                 extraVars.put("EUREKA_CLIENT", "true")
//                 break
//         }

//         def extraVarsString = extraVars.collect { k, v -> "-e ${k}=${v}" }.join(' ')

//         sh """
//         ansible-playbook -i ${inventoryFile} ${playbookFile} ${extraVarsString}
//         """
        
//     } catch (Exception e) {
//         echo "Deployment failed for ${serviceName}: ${e.message}"
//         throw e
//     } finally {
//         sh "rm -rf ${tmpDir}"
//     }
// }

// def validateParameters(String inventoryFile, String playbookFile, String serviceName, 
//                        String image, String namespace, String filePath, 
//                        String domainName, String email) {
//     if (!fileExists(inventoryFile)) {
//         error "Inventory file not found: ${inventoryFile}"
//     }
//     if (!fileExists(playbookFile)) {
//         error "Playbook file not found: ${playbookFile}"
//     }
//     if (!serviceName?.trim()) {
//         error "Service name cannot be empty"
//     }
//     // Add more validation as needed
// }


import groovy.yaml.YamlSlurper

def call(String inventoryFile, String playbookFile, String appName, String image, 
         String namespace, String filePath, String domainName, String email, String gitRepoUrl) {
    
    def tmpDir = "tmp-${appName}-${UUID.randomUUID().toString()}"
    
    try {
        // Clone with credentials if needed
        dir(tmpDir) {
            git(
                url: gitRepoUrl,
                branch: 'main',  // or specify branch as parameter
                credentialsId: 'git-credentials'  // specify your credentials ID
            )
        }
        
        // Detect project type and get port
        def projectInfo = detectProjectType(tmpDir)
        if (!projectInfo) {
            error "Failed to detect project type for repository: ${gitRepoUrl}"
        }
        
        def port = projectInfo.port ?: 8080
        
        echo """
        Project Detection Results:
        -------------------------
        Type: ${projectInfo.type}
        Port: ${port}
        Path: ${tmpDir}
        """

        // Validate parameters
        validateParameters(inventoryFile, playbookFile, appName, image, namespace, filePath, domainName, email)

        // Execute deployment
        sh """
        ansible-playbook -i ${inventoryFile} ${playbookFile} \
        -e "APP_NAME=${appName}" \
        -e "IMAGE=${image}" \
        -e "NAMESPACE=${namespace}" \
        -e "FILE_Path=${filePath}" \
        -e "DOMAIN_NAME=${domainName}" \
        -e "EMAIL=${email}" \
        -e "PORT=${port}"
        """
        
    } catch (Exception e) {
        echo "Deployment failed: ${e.message}"
        throw e
    } finally {
        // Cleanup
        sh "rm -rf ${tmpDir}"
    }
}

def validateParameters(String inventoryFile, String playbookFile, String appName, 
                      String image, String namespace, String filePath, 
                      String domainName, String email) {
    if (!fileExists(inventoryFile)) {
        error "Inventory file not found: ${inventoryFile}"
    }
    if (!fileExists(playbookFile)) {
        error "Playbook file not found: ${playbookFile}"
    }
    if (!appName?.trim()) {
        error "App name cannot be empty"
    }
    // Add more validation as needed
}

def detectProjectType(String projectPath = '.') {
    echo "Detecting project type for path: ${projectPath}"

    try {
        if (fileExists("${projectPath}/package.json")) {
            def packageJson = readJSON file: "${projectPath}/package.json"

            if (packageJson.dependencies?.next || packageJson.devDependencies?.next) {
                return [type: 'nextjs', port: 3000]
            } else if (packageJson.dependencies?.react || packageJson.devDependencies?.react) {
                return [type: 'react', port: 3000]
            }
        } else if (fileExists("${projectPath}/pom.xml")) {
            def port = readSpringBootPortFromYaml(projectPath)
            return [type: 'springboot-maven', port: port]
        } else if (fileExists("${projectPath}/build.gradle") || fileExists("${projectPath}/build.gradle.kts")) {
            def port = readSpringBootPortFromYaml(projectPath)
            return [type: 'springboot-gradle', port: port]
        } else if (fileExists("${projectPath}/pubspec.yaml")) {
            return [type: 'flutter', port: 8080]
        }

        echo "No specific project type detected, using default configuration"
        return [type: 'unknown', port: 8080]
    } catch (Exception e) {
        echo "Error detecting project type: ${e.message}"
        return [type: 'unknown', port: 8080]
    }
}

def readSpringBootPortFromYaml(String projectPath) {
    def defaultPort = 8080
    def yamlFilePath = "${projectPath}/src/main/resources/application.yml"

    if (!fileExists(yamlFilePath)) {
        echo "application.yml not found, using default port: ${defaultPort}"
        return defaultPort
    }

    try {
        def yamlContent = readFile(file: yamlFilePath)
        def yaml = new YamlSlurper().parseText(yamlContent)

        if (yaml?.server?.port) {
            echo "Port found in application.yml: ${yaml.server.port}"
            return yaml.server.port
        } else {
            echo "Port not defined in application.yml, using default port: ${defaultPort}"
        }
    } catch (Exception e) {
        echo "Error reading application.yml: ${e.message}, using default port: ${defaultPort}"
    }

    return defaultPort
}

