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
				case 'Stop':
					location += '/api/catalog/' + catalogId + '/stop';
					break;
			}
			if(location.length > 0){
				console.log(location);
				$.post('${contextPath}/api/catalog/' + catalogId + '/run',null,function(data){
					if(data.data == true && txt != 'Stop'){
						alert('Catalog is running now!');
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
						<tr catalogId="${(bean.catalog.id)!}">
							<td width="3%">
							    ${(page.page - 1) * (page.size) + (bean_index + 1)}
							</td>
							<td width="8%" class="tdLeft5">
								${(bean.catalog.name)!}
							</td>
							<td width="5%" class="tdLeft5">
								${(bean.catalog.cat)!}
							</td>
							<td class="tdLeft5" title="${(bean.url)!}">
								${(bean.catalog.url)!}
							</td>
							<td width="5%" class="tdLeft5">
								${(bean.catalog.pageEncoding)!}
							</td>
							<td width="12%" class="tdLeft5" title="${(bean.pathPattern)!}">
								${(bean.catalog.pathPattern)!}
							</td>
							<td width="12%" class="tdLeft5" title="${(bean.excludedPathPattern)!}">
								${(bean.catalog.excludedPathPattern)!}
							</td>
							<td width="8%">
								${(bean.catalog.maxFetchSize)!}
							</td>
							<td width="6%">
								${(bean.catalog.duration)!}
							</td>
							<td width="10%">
								${(bean.catalog.lastModified? string('yyyy-MM-dd HH:mm:ss'))!}
							</td>
							<td width="12%" class="tdLeft5">
								<#if bean?? && bean.completed?string == 'false'>
									<a class="stopCatalog opCatalog" href="javascript:void(0);">Stop</a>
									<a href="${contextPath}/catalog/${(bean.catalog.id)!}/summary">Realtime</a>
								<#else>
									<a class="editCatalog" href="${contextPath}/catalog/${(bean.catalog.id)!}/edit">Edit</a>
									<a class="deleteCatalog opCatalog" href="javascript:void(0);">Delete</a>
									<a class="cleanCatalog opCatalog" href="javascript:void(0);">Clean</a>
									<#if bean?? && bean.catalog?? && bean.catalog.version gt 0>
										<a class="updateCatalog opCatalog" href="javascript:void(0);">Update</a>
									<#else>
										<a class="rebuildCatalog opCatalog" href="javascript:void(0);">Rebuild</a>
									</#if>
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