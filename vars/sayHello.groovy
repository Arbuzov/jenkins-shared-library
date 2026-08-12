def call(String name = 'human') {
    reportUsage(path: getClass().protectionDomain.codeSource.location.path)
    echo "Hello, ${name}."
}