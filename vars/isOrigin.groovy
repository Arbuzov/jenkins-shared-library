Boolean call() {
    return (env.getAt('GIT_URL')!=null) && (env.getAt('GIT_URL').toLowerCase().contains('vsat/') || env.getAt('GIT_URL').toLowerCase().contains('asat3/'));
}