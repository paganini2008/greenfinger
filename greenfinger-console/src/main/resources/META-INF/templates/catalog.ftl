<#setting number_format="#">
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>Greenfinger Catalog</title>
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
	#selectCat{
		width: 120px;
		height: 28px;
		line-height: 28px;
		padding-left: 5px;
		float: right;
	}

	#createBtn{
		float: left;
	}
</style>
<script type="text/javascript">
	$(function(){
		
		$('#createBtn').click(function(){
			window.location.href='${contextPath}/catalog/edit';
		});
		
		$('#selectCat').change(function(){
			$('#pageNo').val(1);
			$('#searchForm').attr('action','${contextPath}/catalog/list');
			$('#searchForm').submit();
		});
	
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
	
	function initializeSelectCats(){
		var url = '${contextPath}/api/catalog/all/cats';
			$.ajax({
			    url: url,
				type:'get',
				dataType:'json',
				success: function(data){
				    if(data.success == true){
				    	var html = '<option value="">All</option>';
				    	$.each(data.data,function(i,item){
				    		html += '<option value="' + item +'">' + item;
				    		html += '</option>';
				    	});
				    	if(html.length >0){
				    		$('#selectCat').html(html);
				    	}
				    }

				}
			});
	}
	
	function onLoad(){
	
		initializeSelectCats();
	
		$('#searchForm').attr('action','${contextPath}/catalog/list');
		$('#searchForm').submit();
	}
	
</script>
<body>
		<#include "top.ftl">
		<div id="container">
			<div id="searchBox">
				<form class="pageForm" id="searchForm">
					<input type="hidden" value="${(page.page)!1}" name="page" id="pageNo"/>
					<div class="searchCondition">
					</div>
					<div class="searchCondition">
						<select id="selectCat" name="cat">
						</select>
						<input type="button" value="Create" class="cBtn" id="createBtn"/>
					</div>
				</form>
			</div>
			<div id="tabBox">
			</div>
		</div>
		<#include "foot.ftl">
</body>
</html>