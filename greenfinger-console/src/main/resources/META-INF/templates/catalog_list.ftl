<#import "common/page.ftl" as pageToolbar>
<script type="text/javascript">
	$(function(){
		
		TableUtils.initialize(${(page.size)!10});
		
		TableUtils.rowColour();
	
		$('.tblCom').colResizable({
			  liveDrag:true, 
	          gripInnerHtml:"<div class='grip'></div>", 
	          draggingClass:"dragging", 
	          resizeMode:'fit',
	          disabledColumns: [0,9]
		});
		
		$('.opCatalog').click(function(){
			var txt = $(this).text();
			var catalogId = $(this).parent().parent().attr('catalogId');
			var location = '${contextPath}';
			switch(txt){
				case 'Delete':
					location += '/api/catalog/' + catalogId + '/delete';
					break;
				case 'Clean':
					location += '/api/catalog/' + catalogId + '/clean';
					break;
				case 'Update':
					location += '/api/catalog/' + catalogId + '/update';
					break;
				case 'Rebuild':
					location += '/api/catalog/' + catalogId + '/rebuild';
					break;
			}
			if(location.length > 0){
				console.log(location);
				$.post('${contextPath}/api/catalog/' + catalogId + '/run',null,function(data){
					if(data.data == true){
						alert('Running!!!');
						return;
					}else{
						$.ajax({
						    url: location,
							type:'post',
							dataType:'json',
							success: function(data){
							    if(data.success == true){
							    	alert(data.data);
							    }else{
							    	alert(data.errorMsg);
							    }
							    onLoad();
							}
						});
					}
				});
				

			}
		});
		
	})
</script>
<div id="tabContent">
		<table border="0" cellspacing="0" cellpadding="0" class="tblCom" width="100%">
			<thead>
				<tr>
					<td width="3%">
						#
					</td>
					<td width="8%" class="tdLeft5">
						Name
					</td>
					<td width="5%" class="tdLeft5">
						Cat
					</td>
					<td class="tdLeft5">
						URL
					</td>
					<td width="5%" class="tdLeft5">
						Encoding
					</td>
					<td width="12%" class="tdLeft5">
						Path Pattern
					</td>
					<td width="12%" class="tdLeft5">
						Excluded Path Pattern
					</td>
					<td width="8%">
						Fetch Size
					</td>
					<td width="6%">
						Duration
					</td>
					<td width="10%">
						Last Modified
					</td>
					<td width="12%" class="tdLeft5">
						&nbsp;
					</td>
				</tr>
			</thead>
			<tbody>
				<#if page ?? && page.results?? && page.results? size gt 0>
					<#list page.results as bean>
						<tr catalogId="${(bean.id)!}">
							<td width="3%">
							    <a href="${contextPath}/catalog/${(bean.id)!}/summary">${(page.page - 1) * (page.size) + (bean_index + 1)}</a>
							</td>
							<td width="8%" class="tdLeft5">
								${(bean.name)!}
							</td>
							<td width="5%" class="tdLeft5">
								${(bean.cat)!}
							</td>
							<td class="tdLeft5" title="${(bean.url)!}">
								${(bean.url)!}
							</td>
							<td width="5%" class="tdLeft5">
								${(bean.pageEncoding)!}
							</td>
							<td width="12%" class="tdLeft5" title="${(bean.pathPattern)!}">
								${(bean.pathPattern)!}
							</td>
							<td width="12%" class="tdLeft5" title="${(bean.excludedPathPattern)!}">
								${(bean.excludedPathPattern)!}
							</td>
							<td width="8%">
								${(bean.maxFetchSize)!}
							</td>
							<td width="6%">
								${(bean.duration)!}
							</td>
							<td width="10%">
								${(bean.lastModified? string('yyyy-MM-dd HH:mm:ss'))!}
							</td>
							<td width="12%" class="tdLeft5">
								<a class="deleteCatalog opCatalog" href="javascript:void(0);">Delete</a>
								<a class="cleanCatalog opCatalog" href="javascript:void(0);">Clean</a>
								<#if bean?? && bean.version gt 0>
									<a class="updateCatalog opCatalog" href="javascript:void(0);">Update</a>
								<#else>
									<a class="rebuildCatalog opCatalog" href="javascript:void(0);">Rebuild</a>
								</#if>
							</td>
						</tr>
					</#list>
				<#else>
					<tr>
						<td colspan="11">
							<p class="tabNoData">
								No data and please search again.
							</p>
						</td>
					</tr>
				</#if>
			</tbody>
		</table>
</div>
<#if page ?? && page.results?? && page.results? size gt 0>
	<@pageToolbar.page page = page display = 0/> 
</#if>