def call(String inventoryFile, String playbookFile, String serviceName, String image, 
         String namespace, String filePath, String domainName, String email, String gitRepoUrl) {
    
    def tmpDir = "tmp-${serviceName}-${UUID.randomUUID().toString()}"
    
    try {
        dir(tmpDir) {
            git(url: gitRepoUrl, branch: 'main', credentialsId: 'git-credentials')
        }
        
        def projectInfo = detectProjectType(tmpDir)
        if (!projectInfo) {
            error "Failed to detect project type for repository: ${gitRepoUrl}"
        }
        
        def port = projectInfo.port ?: 8080
        
        echo """
        Project Detection Results for ${serviceName}:
        -------------------------
        Type: ${projectInfo.type}
        Port: ${port}
        Path: ${tmpDir}
        """

        validateParameters(inventoryFile, playbookFile, serviceName, image, namespace, filePath, domainName, email)

        def extraVars = [
            "SERVICE_NAME": serviceName,
            "IMAGE": image,
            "NAMESPACE": namespace,
            "FILE_Path": filePath,
            "DOMAIN_NAME": domainName,
            "EMAIL": email,
            "PORT": port
        ]

        // Add service-specific configurations
        switch(serviceName) {
            case 'eureka':
                extraVars.put("EUREKA_ROLE", "server")
                break
            case 'config-server':
                extraVars.put("CONFIG_REPO", "https://github.com/your-org/config-repo.git")
                break
            case 'gateway':
                extraVars.put("GATEWAY_ROUTES", "service1,service2,service3")
                break
            default:
                extraVars.put("EUREKA_CLIENT", "true")
                break
        }

        def extraVarsString = extraVars.collect { k, v -> "-e ${k}=${v}" }.join(' ')

        sh """
        ansible-playbook -i ${inventoryFile} ${playbookFile} ${extraVarsString}
        """
        
    } catch (Exception e) {
        echo "Deployment failed for ${serviceName}: ${e.message}"
        throw e
    } finally {
        sh "rm -rf ${tmpDir}"
    }
}

def validateParameters(String inventoryFile, String playbookFile, String serviceName, 
                       String image, String namespace, String filePath, 
                       String domainName, String email) {
    if (!fileExists(inventoryFile)) {
        error "Inventory file not found: ${inventoryFile}"
    }
    if (!fileExists(playbookFile)) {
        error "Playbook file not found: ${playbookFile}"
    }
    if (!serviceName?.trim()) {
        error "Service name cannot be empty"
    }
    // Add more validation as needed
}

