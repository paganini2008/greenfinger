<#setting number_format="#">
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>Greenfinger Searcher</title>
<link rel="shortcut icon" href="#"/>
<script type="text/javascript">
	var $contextPath = '${contextPath}';
</script>
<link href="${contextPath}/static/css/base.css" rel="stylesheet" type="text/css" />
<script type="text/javascript" src="${contextPath}/static/js/lib/jquery-1.7.1.min.js"></script>
<script type="text/javascript" src="${contextPath}/static/js/lib/json2.js"></script>
</head>
<script>
	$(function(){
	
		$('#searchForm').submit(function(){
			var obj = $(this);
			var url = obj.attr('action');
			$.ajax({
			    url: url,
				type:'post',
				dataType:'html',
				data: obj.serialize(),
				success: function(data){
				    $('#searchResult').html(data);
				}
			});
			return false;
		});
	});
</script>
<body>
	<#include "../top.ftl">
	<div id="container">
		<div id="searchBox">
			<form id="searchForm" class="pageForm" action="${contextPath}/index/searcher/search" method="post">
				<input type="hidden" value="${(page.page)!1}" name="page" id="pageNo"/>
				<input type="text" name="q" value="" id="keyword"/>
				<input type="submit" value="Search" id="searchBtn"></input>
			</form>
		</div>
		<div id="searchResult">
		</div>
	</div>
	<#include "../foot.ftl">
</body>
</html>