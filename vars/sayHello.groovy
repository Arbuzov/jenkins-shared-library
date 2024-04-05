def call(String name = 'human') {
    reportUsage(getClass().protectionDomain.codeSource.location.path)
    echo "Hello, ${name}."
}