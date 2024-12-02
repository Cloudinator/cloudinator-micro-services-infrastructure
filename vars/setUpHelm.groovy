def call(String inventoryFile, String playbookFile, String serviceName, String image, 
         String namespace, String tag, String domainName, String gitRepoUrl) {
    
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

        // Validate parameters
        if (!fileExists(inventoryFile)) {
            error "Inventory file not found: ${inventoryFile}"
        }
        if (!fileExists(playbookFile)) {
            error "Playbook file not found: ${playbookFile}"
        }
        if (!serviceName?.trim()) {
            error "Service name cannot be empty"
        }

        // Execute deployment
        sh """
        ansible-playbook -i ${inventoryFile} ${playbookFile} \
        -e "CHART_NAME=${serviceName}" \
        -e "IMAGE=${image}" \
        -e "TAG=${tag}" \
        -e "PORT=${port}" \
        -e "NAMESPACE=${namespace}" \
        -e "HOST=${domainName}" 
        """
        
    } catch (Exception e) {
        echo "Helm setup failed for ${serviceName}: ${e.message}"
        throw e
    } finally {
        sh "rm -rf ${tmpDir}"
    }
}

