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
<style type="text/css">
	#searchBtn {
		font-size: 13px;
		float: right;
		margin: auto 10px;
	}
	
	#selectCat{
		width: 120px;
		height: 28px;
		line-height: 28px;
		padding-left: 5px;
		float: right;
		margin: auto 10px;
	}
</style>
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
		
		$('#selectCat').change(function(){
			$('#keyword').val('');
			$('#pageNo').val(1);
			$('#searchForm').submit();
		});
		
		$('#searchBtn').click(function(){
			$('#pageNo').val(1);
			$('#searchForm').submit();
		});
		
		onLoad();
	});
	
	function initializeSelectCats(){
		var url = '${contextPath}/api/catalog/all/cats';
			$.ajax({
			    url: url,
				type:'get',
				dataType:'json',
				success: function(data){
				    if(data.success == true){
				    	var html = '';
				    	$.each(data.data,function(i,item){
				    		html += '<option>' + item;
				    		html += '</option>';
				    	});
				    	if(html.length >0){
				    		$(html).appendTo($('#selectCat'));
				    		$('#selectCat').change();
				    	}
				    }

				}
			});
	}
	
	function onLoad(){
		initializeSelectCats();
	}
</script>
<body>
	<#include "../top.ftl">
	<div id="container">
		<div id="searchBox">			
			<form id="searchForm" class="pageForm" action="${contextPath}/index/searcher/search" method="post">
				<input type="hidden" value="${(page.page)!1}" name="page" id="pageNo"/>
				<div class="searchCondition">
					<select id="selectCat" name="cat">
					</select>
					<label style="height: 32px; line-height: 32px; font-weight: 800; float: right; padding-right: 5px; display: inline-block;">Choose Category: </label>
				</div>
				<div class="searchCondition">
					<input type="text" name="q" value="" id="keyword"/>
					<input type="button" value="Search" id="searchBtn" class="cBtn"></input>
				</div>
			</form>
		</div>
		<div id="searchResult">
		</div>
	</div>
	<#include "../foot.ftl">
</body>
</html>