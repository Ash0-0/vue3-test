pipeline {
    agent {
        docker {
            image 'docker:latest' // 使用 Node.js 20 官方镜像
            args '-v /var/run/docker.sock:/var/run/docker.sock' // 挂载 Docker Socket 支持 Docker 命令
        }
    }

    environment {
            PATH = "/usr/local/bin:$PATH"
            CACHE_DIR = '.npm_cache' // npm 缓存目录
            DOCKER_REGISTRY = 'docker.io' // Docker Hub 注册表地址
            CREDENTIALS_ID = 'docker-hub-credentials' // Jenkins 中配置的凭据 ID
    }


    stages {
      stage('Checkout Code') {
            steps {
                echo "Pulling code from public repository via HTTPS..."
                git branch: 'main',
                url: 'https://github.com/Ash0-0/vue3-test.git'
              }
        }
        stage('Install Node.js and npm') {
                    steps {
                        echo "Installing Node.js and npm..."
                        sh """
                        # Install Node.js (which includes npm)
                        apk add --no-cache nodejs npm
                        """
                    }
                }

        stage('Generate Version') {
            steps {
                echo "Generating new version..."
                script {
                    // Generate version number using the current date and Jenkins build number
                    def BUILD_DATE_FORMATTED = new Date().format('yyyyMMdd') // Current date in yyyyMMdd format
                    def BUILD_NUMBER = env.BUILD_NUMBER // Jenkins build number
                    def NEW_VERSION = "${BUILD_DATE_FORMATTED}-build-${BUILD_NUMBER}"
                    echo "Generated Version: ${NEW_VERSION}"
                    env.IMAGE_TAG = "vben-admin-local:${NEW_VERSION}" // 设置镜像 Tag
                }
            }
        }

        stage('Install Dependencies') {
            steps {
                echo "Installing dependencies..."
                // 安装项目依赖，启用缓存
                sh '''
                npm config set cache ${CACHE_DIR}
                if [ -d node_modules ]; then
                    echo "Dependencies already installed, skipping..."
                else
                    pnpm install --prefer-offline
                fi
                '''
            }
        }

//         stage('Code Check') {
//             steps {
//                 echo "Running Check..."
//                 // 检查代码规范
//                 sh '''
//                 pnpm run check || { echo "Code Check Failed"; exit 1; }
//                 '''
//             }
//         }

        stage('Build Docker Image') {
            steps {
                echo "Building Docker image: ${env.IMAGE_TAG}"
                // Use triple double quotes to allow Groovy variable interpolation in shell script
                sh """
                docker build -f Dockerfile . -t ${env.IMAGE_TAG}
                """
            }
        }

        stage('Push Docker Image') {
            steps {
                echo "Pushing Docker image to registry..."
                script {
                    withCredentials([usernamePassword(credentialsId: env.CREDENTIALS_ID, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                        // 登录 Docker Registry
                        sh "echo $PASSWORD | docker login ${DOCKER_REGISTRY} -u $USERNAME --password-stdin"

                        // 推送镜像
                        sh "docker push ${env.IMAGE_TAG}"
                    }
                }
            }
        }
    }

    post {
        success {
            echo '''
            Docker image built and tests passed successfully!
            Image Tag: ${env.IMAGE_TAG}
            '''
        }
        failure {
            echo "Build or tests failed. Check the logs for more details."
        }
    }
}
