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
<script type="text/javascript" src="${contextPath}/static/js/lib/colResizable-1.6.min.js"></script>
<script type="text/javascript" src="${contextPath}/static/js/common.js"></script>
</head>
<style type="text/css">

    #searchBox{
    	height: 60px;
    	width: 100%;
    	clear: both;
    }

	#tabBox {
		height: auto;
		width: 100%;
		position: relative;
		bottom: 5px;
	}
	    
	
	#tabContent{
		height: auto;
	}
	
	#saveBtn{
		width: calc(100% - 10px);
		height: 36px;
		line-height: 36px;
		padding: 5px auto;
		cursor: pointer;
		text-align: center;
		font-weight: 800;
		float: left;
		display: inline-block;
		margin: 10px auto;
		background-color: #97CBFF;
	}
	    
</style>
<script type="text/javascript">
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
				    $('#tabBox').html(data);

				}
			});
			return false;
		});
	
		onLoad();
	});
	
	function onLoad(){
		$('#searchForm').attr('action','${contextPath}/catalog/list');
		$('#searchForm').submit();
	}
	
</script>
<body>
		<#include "top.ftl">
		<div id="container">
			<div id="searchBox">
				<form class="pageForm" id="searchForm">
					<input type="hidden" value="${(page.page)!}" name="page" id="pageNo"/>
				</form>
			</div>
			<div id="tabBox">
			</div>
		</div>
		<#include "foot.ftl">
</body>
</html>