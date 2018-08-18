def call(ArrayList stringList, ArrayList rowBrace = ["<li>", "</li>"], ArrayList listBrace = ["<ul style='list-style-type:none'>","</ul>"]){
	String result = "";
	stringList.each{
		result +=(rowBrace[ 0 ] + "<a href='${it}' target='_blank'>" + it + "</a>"+rowBrace[ 1 ])
	}
	return listBrace[ 0 ] + result + listBrace[ 1 ];
}