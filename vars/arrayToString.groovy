String call(ArrayList stringList, ArrayList rowBrace = ["", ""], ArrayList listBrace = ["",""]) {
	String result = "";
	stringList.each{
		result +=(rowBrace[ 0 ] + it + rowBrace[ 1 ]);
	}
	return listBrace[ 0 ] + result + listBrace[ 1 ];
}
