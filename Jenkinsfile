#!groovy
podTemplate(
    inheritFrom: "maven",
    label: "myJenkinsMavenSlave",
    cloud: "openshift",
    volumes: [
        persistentVolumeClaim(claimName: "m2repo", mountPath: "/home/jenkins/.m2/repository")
    ]) {

  node('myJenkinsMavenSlave') {

    def mvnCmd = "mvn -s ./nexus_openshift_settings.xml"

    stage('Checkout codigo fonte') {
      echo "Checkout"
      checkout scm
    }

    def groupId    = getGroupIdFromPom("pom.xml")
    def artifactId = getArtifactIdFromPom("pom.xml")
    def version    = getVersionFromPom("pom.xml")

    stage('Testes unitarios') {
      echo "Unit Tests"
      sh "${mvnCmd} test"
    }

    stage('Analise de codigo') {
     echo "Code Analysis"
     sh "${mvnCmd} sonar:sonar -Dsonar.host.url=http://sonarqube.continuous-integration.svc.cluster.local:9000/ -Dsonar.projectName=rhforum"
    }

    stage('Build WAR') {
      echo "Building version ${version}"
      sh "${mvnCmd} clean package -DskipTests"
    }

    stage('Publicando no Nexus') {
      echo "Publish to Nexus"
      sh "${mvnCmd} deploy -DskipTests=true -DaltDeploymentRepository=nexus::default::http://nexus.continuous-integration.svc.cluster.local:8081/repository/maven-releases"
    }

    stage('Build Imagem Openshift') {
      def newTag = "TestingCandidate-${version}"
      echo "New Tag: ${newTag}"
      sh "mkdir -p ./builddir/deployments"
      sh "cp ./target/rhforum-swarm.jar ./builddir/deployments"
      sh "oc policy add-role-to-user edit system:serviceaccount:continuous-integration:jenkins -n rhforum-app-dev"
      sh "oc new-build --image-stream=redhat-openjdk18-openshift --binary=true --name rhforum -n rhforum-app-dev || echo 'Build já existe'"
      sh "oc start-build rhforum --follow --from-dir=./builddir/. -n rhforum-app-dev"

      openshiftTag alias: 'false', destStream: 'rhforum', destTag: newTag, destinationNamespace: 'rhforum-app-dev', namespace: 'rhforum-app-dev', srcStream: 'rhforum', srcTag: 'latest', verbose: 'false'
    }

    stage('Deploy em Desenvolvimento') {
      sh "oc new-app rhforum-app-dev/rhforum:TestingCandidate-1.0 --name=rhforum --allow-missing-imagestream-tags=true -n rhforum-app-dev || echo 'app já existe'"
      sh "oc set env dc/rhforum JAVA_OPTIONS=\"-Djava.net.preferIPv4Stack=true -Dswarm.context.path=/rhforum\" -n rhforum-app-dev"
      sh "oc patch dc rhforum --patch '{\"spec\": { \"triggers\": [ { \"type\": \"ImageChange\", \"imageChangeParams\": { \"containerNames\": [ \"rhforum\" ], \"from\": { \"kind\": \"ImageStreamTag\", \"namespace\": \"rhforum-app-dev\", \"name\": \"rhforum:TestingCandidate-$version\"}}}]}}' -n rhforum-app-dev"
      sh "oc set triggers dc/rhforum --remove-all -n rhforum-app-dev"
      sh "oc expose dc rhforum --port 8080 -n rhforum-app-dev || echo 'svc já existe'"
      sh "oc expose svc rhforum --path=/rhforum -n rhforum-app-dev || echo 'route já existe'"
      //openshiftDeploy depCfg: 'rhforum', namespace: 'rhforum-app-dev', verbose: 'false', waitTime: '', waitUnit: 'sec'
      openshiftVerifyDeployment depCfg: 'rhforum', namespace: 'rhforum-app-dev', replicaCount: '1', verbose: 'false', verifyReplicaCount: 'false', waitTime: '', waitUnit: 'sec'
      openshiftVerifyService namespace: 'rhforum-app-dev', svcName: 'rhforum', verbose: 'false'
      //sh "oc expose service rhforum --path=/rhforum -n rhforum-app-dev || echo 'Rota já existe'"
    }

    stage('Testes de Integracao') {
      def newTag = "ProdReady-${version}"
      echo "New Tag: ${newTag}"

      openshiftTag alias: 'false', destStream: 'rhforum', destTag: newTag, destinationNamespace: 'rhforum-app-dev', namespace: 'rhforum-app-dev', srcStream: 'rhforum', srcTag: 'latest', verbose: 'false'

      timeout(120) {
            waitUntil {

                def r = sh (
                    script: 'curl -I -s http://rhforum-rhforum-app-dev.apps.ocp.rhbrlab.com/rhforum/rest/clientes | head -n 1 |cut -d$\' \' -f2',
                    returnStdout: true
                ).trim()
                return r.toInteger().equals(200);
            }
      }

      sh "${mvnCmd} com.restlet.dhc:dhc-maven-plugin:1.4.1:test@default-cli -s nexus_openshift_settings.xml"
    }

    def dest   = "rhforum-green"
    def active = ""

    stage('Preparando deploy em Producao') {
      sh "oc policy add-role-to-group system:image-puller system:serviceaccounts:rhforum-app-prod -n rhforum-app-dev"
      sh "oc policy add-role-to-user edit system:serviceaccount:continuous-integration:jenkins -n rhforum-app-prod"
      sh "oc new-app rhforum-app-dev/rhforum:ProdReady-1.0 --name=rhforum-green --allow-missing-imagestream-tags=true -n rhforum-app-prod || echo 'app já existe'"
      sh "oc new-app rhforum-app-dev/rhforum:ProdReady-1.0 --name=rhforum-blue --allow-missing-imagestream-tags=true -n rhforum-app-prod || echo 'app já existe'"
      sh "oc set env dc/rhforum-green JAVA_OPTIONS=\"-Djava.net.preferIPv4Stack=true -Dswarm.context.path=/rhforum\" -n rhforum-app-prod"
      sh "oc set env dc/rhforum-blue JAVA_OPTIONS=\"-Djava.net.preferIPv4Stack=true -Dswarm.context.path=/rhforum\" -n rhforum-app-prod"


      sh "oc set resources dc/rhforum-green --limits=cpu='1000m',memory='750Mi' --requests=cpu='250m',memory='200Mi' -n rhforum-app-prod || echo 'Limit e CPUs já definidos!'"
      sh "oc set resources dc/rhforum-blue --limits=cpu='1000m',memory='750Mi' --requests=cpu='250m',memory='200Mi' -n rhforum-app-prod || echo 'Limit e CPUs já definidos!'"
      sh "oc set probe dc rhforum-green --readiness --initial-delay-seconds=1 --timeout-seconds=10 --get-url=http://:8080/node -n rhforum-app-prod || echo 'Readiness check já existe para o sistema!'"
      sh "oc set probe dc rhforum-blue --readiness --initial-delay-seconds=1 --timeout-seconds=10 --get-url=http://:8080/node -n rhforum-app-prod || echo 'Readiness check já existe para o sistema!'"
      sh "oc set probe dc rhforum-green --liveness --initial-delay-seconds=120 --timeout-seconds=10 --get-url=http://:8080/node -n rhforum-app-prod || echo 'Liveness check já existe para o sistema!'"
      sh "oc set probe dc rhforum-blue --liveness --initial-delay-seconds=120 --timeout-seconds=10 --get-url=http://:8080/node -n rhforum-app-prod || echo 'Liveness check já existe para o sistema!'"
      sh "oc autoscale dc rhforum-blue --cpu-percent=90 --min=1 --max=5 -n rhforum-app-prod || echo 'Autoscaler já existe!'"
      sh "oc autoscale dc rhforum-green --cpu-percent=90 --min=1 --max=5 -n rhforum-app-prod || echo 'Autoscaler já existe!'"


      sh "oc set triggers dc/rhforum-green --remove-all -n rhforum-app-prod"
      sh "oc set triggers dc/rhforum-blue --remove-all -n rhforum-app-prod"
      sh "oc expose dc rhforum-blue --port 8080 -n rhforum-app-prod || echo 'svc já existe'"
      sh "oc expose dc rhforum-green --port 8080 -n rhforum-app-prod || echo 'svc já existe'"
      sh "oc expose svc/rhforum-green --path=/rhforum --name rhforum -n rhforum-app-prod || echo 'route já existe'"


      sh "oc get route rhforum -n rhforum-app-prod -o jsonpath='{ .spec.to.name }' > activesvc.txt"
      active = readFile('activesvc.txt').trim()
      if (active == "rhforum-green") {
        dest = "rhforum-blue"
      }
      echo "Active svc: " + active
      echo "Dest svc:   " + dest
    }

    stage('Deploy em Producao') {
      echo "Deploying to ${dest}"

      sh "oc patch dc ${dest} --patch '{\"spec\": { \"triggers\": [ { \"type\": \"ImageChange\", \"imageChangeParams\": { \"containerNames\": [ \"$dest\" ], \"from\": { \"kind\": \"ImageStreamTag\", \"namespace\": \"rhforum-app-dev\", \"name\": \"rhforum:ProdReady-$version\"}}}]}}' -n rhforum-app-prod"

      sh "oc expose service ${dest} --path=/rhforum -n rhforum-app-prod || echo 'Rota já existe'"

      openshiftDeploy depCfg: dest, namespace: 'rhforum-app-prod', verbose: 'false', waitTime: '', waitUnit: 'sec'
      openshiftVerifyDeployment depCfg: dest, namespace: 'rhforum-app-prod', replicaCount: '1', verbose: 'false', verifyReplicaCount: 'true', waitTime: '', waitUnit: 'sec'
      openshiftVerifyService namespace: 'rhforum-app-prod', svcName: dest, verbose: 'false'

    }

    stage('Testes de Performance') {
      sh "${mvnCmd} jmeter:jmeter"

      if(getAvg() < 100){
         input "Publicar em produção mesmo com a performance abaixo do esperado?"
      }
    }

    stage('Mudando para a nova versao') {
      sh 'oc patch route rhforum -n rhforum-app-prod -p \'{"spec":{"to":{"name":"' + dest + '"}}}\''
      sh 'oc get route rhforum -n rhforum-app-prod > oc_out.txt'
      oc_out = readFile('oc_out.txt')
      echo "Current route configuration: " + oc_out
    }
  }
}

// Convenience Functions to read variables from the pom.xml
def getVersionFromPom(pom) {
  def matcher = readFile(pom) =~ '<version>(.+)</version>'
  matcher ? matcher[0][1] : null
}
def getGroupIdFromPom(pom) {
  def matcher = readFile(pom) =~ '<groupId>(.+)</groupId>'
  matcher ? matcher[0][1] : null
}
def getArtifactIdFromPom(pom) {
  def matcher = readFile(pom) =~ '<artifactId>(.+)</artifactId>'
  matcher ? matcher[0][1] : null
}

def getAvg() {
    def result = sh (
        script: 'grep "summary =" ./target/jmeter/logs/clientes.jmx.log | awk \'{ print \$11}\' | tr -d \'/s\' | cut -d. -f1',
        returnStdout: true
    ).trim()
    echo "media = ${result}"
    return result.toInteger();
}
