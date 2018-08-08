def call() {
	String result = "";
	def uriStr = (env.getAt('GIT_URL')!=null && !env.getAt('GIT_URL').equals(""))?env.getAt('GIT_URL'):env.getAt('GIT_URL_1');
	if (uriStr!=null && !uriStr.equals("")) {
		result = uriStr.split(":").getAt(1).split("/").getAt(0);
	}
	return result;
}
