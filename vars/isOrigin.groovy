Boolean call() {
    return (env.getAt('GIT_URL')!=null) && env.getAt('GIT_URL').toLowerCase().contains('vsat/');
}