/*
 * Copyright (c) 2012 Yahoo! Inc. All rights reserved.
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is 
 *  distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the License.
 *  See accompanying LICENSE file.
 */

String.prototype.startsWith = function(str) {
	return (this.match("^" + str) == str)
}

var stats;
var fieldShortNames;
var fieldLongNames;
var store = {};
var ns;
var webapp = "";
var RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
var contextsToColour = []; // TODO populate from server.

var argString = window.location.href.split('?')[1];
var args = []; // URL arguments
if (argString != undefined) {
	args = parse(argString);
}

function getProviderName(sstring) {
	if (contextsToColour[sstring] === undefined)
		return getLocalName(sstring);
	else
		return contextsToColour[sstring];
}

function getLocalName(uri) {
	if (uri.indexOf('#') > 0) {
		return uri.substring(uri.lastIndexOf('#') + 1);
	} else if (uri.indexOf('/') > 0) {
		return uri.substring(uri.lastIndexOf('/') + 1);
	} else
		return uri;
}

function stripVersion(uri) {
	return uri.replace(/[0-9]+\.[0-9]+\.[0-9]+\//, "");
}

function encode(uri) {
	return uri.replace(/[^a-zA-Z0-9]+/g, "_");
}

function parse(qs, sep, eq) {
	sep = sep || "&";
	eq = eq || "=";
	for ( var obj = {}, i = 0, pieces = qs.split(sep), l = pieces.length, tuple; i < l; i++) {
		tuple = pieces[i].split(eq);
		if (tuple.length > 0) {
			obj[unescape(tuple.shift())] = unescape(tuple.join(eq));
		}
	}
	return obj;
};

function unescape(s) {
	return decodeURIComponent(s.replace(/\+/g, ' '));
};

YUI({
	gallery : 'gallery-2011.01.03-18-30'
}).use(
	'gallery-yui3treeview',
	'history',
	'tabview',
	'node',
	'datatable-scroll',
	'event',
	'io-base',
	'json-parse',
	'json-stringify',
	'querystring-parse-simple',
	'autocomplete',
	'autocomplete-filters',
	'autocomplete-highlighters',
	
	function(Y) {
		Y.HistoryHash.hashPrefix = '!';
		var history = new Y.HistoryHash();

		function initDataSet() {
			fieldShortNames = [ "any" ];
			fieldLongNames = [ "any" ];
			Y.one("#results").setContent("");
			Y.one("#result-stats").setContent("");
			Y.one("#class-select").setContent("");
			Y.one("#resultContainer").hide();

			Y.one("#statistics-tree").setContent("");
			Y.one("#statistics-loader").show();

			var indexStatisticsConfig = {
				data: {
					'index': Y.one("#dataset").get('value')
				},
				on: {
					success : function(transactionid, response, arguments) {
						stats = Y.JSON.parse(response.response);

						Y.one("#class-loader").hide();
						Y.one("#class-message").setContent("");

						Y.one("#statistics-loader").hide();

						var autocompletefields = [ 'OR' ];
						for ( var key in stats.fields) {
							fieldShortNames.push(key);
							fieldLongNames.push(stats.fields[key]);
							// fill up the autocomplete
							// suggestion box
							autocompletefields.push(key);
						}

						Y.one('#ac-input').plug(Y.Plugin.AutoComplete, {
							resultFilters : 'phraseMatch',
							queryDelimiter : ' ',
							tabSelect : 'true',
							typeAhead : 'true',
							source : autocompletefields
						});

						// Display class selection dropdown
						if (stats.classes == undefined) {
							// Render TreeView
							Y.one('#statistics-tree').setContent('<p>There are no rdf:type tuples in this dataset.</p>');
							
							// Hide Ontology Search box
							Y.one('#ontologysearch').hide();
							
						} else {
							var classesWithProperties = [];
							for (className in stats.classes) {
								var clazz = stats.classes[className];
								// Add className field to each class.
								clazz.className = className;
								
								// Change all children class names to references +
								// for all classes that are children add a ref to the parent classes.
								for (i in clazz.children) {
									var childName = clazz.children[i];
									var childClass = stats.classes[childName];
									clazz.children[i] = childClass;
									if (childClass.parents == undefined) {
										childClass.parents = [];
									}
									childClass.parents.push(clazz);
								}
								
								// get classes with properties.
								if (clazz.properties != undefined) {
									classesWithProperties.push(clazz);
								}
							}
							
							// Sort by the local name
							classesWithProperties.sort(function(a,b) {
							    if(a.localName<b.localName) return -1;
							    if(a.localName>b.localName) return 1;
							    return 0;
							})
							
							var frag = '<select>';
							for (i in classesWithProperties) {
								clazz = classesWithProperties[i];
								frag = frag + "<option value='" + i + "'>" + clazz.localName + " - " + clazz.className + "</option>";
							}
							frag = frag + '</select>';
							var fragNode = Y.Node.create(frag);
							Y.one('#class-select').append("I'm looking for a").append(fragNode).append("where");
							
							changeProperties(classesWithProperties[0]);
							fragNode.on('change', function(e) {
								var i = e.target.get('value');
								changeProperties(classesWithProperties[i]);
							});

							var tree = [];
							function addClass(clazz) {
								var rt = {};
								
								if (clazz == undefined) {
									return rt;
								}
								
								rt['label'] = clazz.localName + " " + clazz.inheritedCount;
								if (clazz.inheritedCount != clazz.count) {
									rt['label'] +=  "(" + clazz.count + ")";
								}
								
								var children = [];
								for (i in clazz.children) {
									var childClass = clazz.children[i];
									children.push(addClass(childClass));
								}
								if (children.length > 0) {
									rt['type'] = 'TreeView';
									rt['children'] = children
								}
								return rt;
							}
	
							// Render TreeView
							Y.one('#statistics-tree').setContent('<ul id="statisticsTreeList"></ul>');
							for (i in stats.rootClasses) {
								var className = stats.rootClasses[i];
								var clazz = stats.classes[className];
								tree.push(addClass(clazz));
							}
							var treeview = new Y.TreeView({
								srcNode : '#statisticsTreeList',
								contentBox : null,
								type : "TreeView",
								children : tree
							});
	
							treeview.render();
							// Expand the top level of the tree
							Y.one('#statistics-tree').removeClass('yui3-tree-collapsed');
							
							// Toggle ontology search box
							Y.one('#ontologysearch').show();
							Y.one('#toggleonto').on('click', function(e) {
								var node = Y.one('#onto-hider');
								var button = Y.one('#toggleonto');
								if (button.getContent() == "Hide") {
									node.hide();
									button.setContent("Show");
								} else {
									node.show();
									button.setContent("Hide");
								}
							});
						}

						// Render statistics box
						/*
						 * Y.one("#statistics").setContent(''); var sidestats =
						 * [];a for (clazz in keys) { sidestats.push({"Entity" :
						 * getLocalName(keys[clazz]), "Count":
						 * stats.classes[keys[clazz]].count}); } var
						 * dtScrollingY = new Y.DataTable.Base({ columnset : [{
						 * key : "Entity", label : "Entity" }, { key : "Count",
						 * label : "Count" }], recordset : sidestats, summary :
						 * "Y axis scrolling table" });
						 * dtScrollingY.plug(Y.Plugin.DataTableScroll, { height :
						 * "400px" }); dtScrollingY.render("#statistics");
						 */
					},
					failure : function(transactionid, response, arguments) {
						Y.one("#statistics-loader").hide();
						alert("Failed to get index stats from server. " + response);
					}
				}
			}
			
			Y.io('ajax/indexStatistics', indexStatisticsConfig);
		}

		function getDocumentByIdOrSubject(idOrSubject) {
			Y.one("#ac-input").set('value', "doc:" + idOrSubject);
			executeUnifiedSearch(null);
		}
		
		function executeUnifiedSearch(e) {
			var query = Y.one("#ac-input").get('value');
			loadResults(query);
		}

		function executeSearchByClass(e) {
			var query = '';
			var params = Y.all(".class-property").each(function(thisNode) {
				if (thisNode.get('value') != '') {
					query = query + ' ';
					query = query + ns + thisNode.get('name') + ':' + thisNode.get('value');
				}
			});
			loadResults(query);
		}

		function loadResults(query) {
			history.add({
				'index': Y.one("#dataset").get('value'),
				'query': query,
				'pageSize': Y.one("#numresults").get('value'),
				'deref': Y.one("#dereference").get('checked'),
			});
		}

		function doQuery(paramsMap) {
			if (paramsMap['index'] == undefined || paramsMap['query'] == undefined) { // || paramsMap['query'].length == 0) {
				Y.one("#result-loader").hide();
				Y.one("#resultContainer").hide();
				return;
			}
			
			Y.one("#result-loader").show();
			
			var doQueryConfig = {
				data: paramsMap,
				on: {
					success : function(transactionid, response, arguments) {
						var result = Y.JSON.parse(response.response);
						
						Y.one("#result-loader").hide();
						Y.one("#resultContainer").show();
						Y.one("#result-stats").setContent("Found " + result.numResults + " results in " + result.time + " ms.");

						var ol = Y.Node.create("<ol></ol>");

						var markers = [];
						for ( var i in result.resultItems) {
							renderResult(result.resultItems[i], ol);
						}

						Y.one("#results").setContent("").append(ol);
					},
					failure : function(transactionid, response, arguments) {
						var message = "";
						if (response != undefined) {
							message = Y.JSON.parse(response.response);
						}
						alert("Failed to get results from server. " + message);
						Y.one("#result-loader").hide();
						Y.one("#resultContainer").hide();
					}
				}
			}
			Y.io('ajax/query', doQueryConfig);
		}

		function renderResult(result, node) {
			var li = Y.Node.create("<li class=\"result\"></li>");

			function predSort(a, b) {
				return (fieldLongNames.indexOf(encode(a.predicate)) - fieldLongNames.indexOf(encode(b.predicate)));
			}
			result.relations.sort(predSort);

			// Group the relations by predicate
			var map = [];
			var types = [];

			if (result.hasOwnProperty("relations")) {
				for (relationIndex in result.relations) {
					var relation = result.relations[relationIndex];
					if (map[relation.predicate] == null) {
						map[relation.predicate] = [ relation ];
					} else {
						map[relation.predicate].push(relation);
					}
					if (relation.predicate == RDF_TYPE) {
						types.push(stripVersion(relation.object));
					}
				}
			}
			function typeSort(a, b) {
				return stats.classes[b].count - stats.classes[a].count;
			}
			types.sort(typeSort);

			if (result.hasOwnProperty("label") && result.label != null) {
				li.append('<span class="label">' + result.label + '</span>');
			}
			li.append("<br/>");
			for (type in types) {
				li.append('&nbsp;<a class="type" href="' + types[type] + '">' + getLocalName(types[type]) + '</a>&nbsp;/');
			}
			var span = Y.Node.create('<span class="id"><a href=' + result.subject + '>' + result.subject + '</a></span>');
			if (result.subjectId != undefined) {
				appendDocLink(span, result.subjectId);
			}
			li.append(span);

			var table = Y.Node
					.create('<table class=\"result\"><col class=\"predicate-col\"/><col class=\"value-col\"/><th class="result-header">Property</th><th class=\"result-header\">Value</th></table>');
			var i = 0;
			for (var predicate in map) {
				if (predicate == RDF_TYPE)
					continue;
				
				var tdPredicate = Y.Node.create('<td class="predicate">' + getLocalName(predicate) + '</td>');
				
				var tdValues = Y.Node.create('<td class="object"></td>');
				for ( var relationIndex in map[predicate]) {
					var item = map[predicate][relationIndex];
					var providedName = getProviderName(item.context[0]);
					var div = Y.Node.create('<div title="' + providedName + '" class="source-' + providedName + '"></div>');
					div.appendChild(renderValue(item.object, item.label));
					if (item.subjectIdOfObject != undefined) {
						appendDocLink(div, item.subjectIdOfObject);
					}
					tdValues.appendChild(div);
				}
				
				var tr;
				if (i++ % 2 == 0) {
					tr = Y.Node.create('<tr class="even"></tr>');
				} else {
					tr = Y.Node.create('<tr class="odd"></tr>');
				}
				tr.appendChild(tdPredicate);
				tr.appendChild(tdValues);
				table.appendChild(tr);
			}

			li.append(table);
			node.append(li);

		}

		function appendDocLink(parent, docId) {
			var func = getDocumentByIdOrSubject;
			var param = docId;
			var closure = function() {
				func(param);
			}
			var element = Y.Node.create('<span class="doc-link">&nbsp;-&gt;</span>');
			element.on('click', closure);
			parent.appendChild(element);
		}
		
		// A value (URI or Literal) to be rendered. URI optionally
		// has anchortext
		function renderValue(ref, value) {
			if (ref.startsWith("urn:uuid:")) {
				ref = ref.substring(9);
				if (value == undefined) {
					value = ref;
				}
				var kbname = Y.one("#dataset").get('value');
				return '<a href="search.html?index=' + kbname + '&subject=' + ref + '">' + value + '</a>';
			}

			if (value == undefined) {
				value = ref;
			}
			if (ref.startsWith("http:")) {
				return '<a href="' + ref + '">' + value + '</a>';
			}
			return value;
		}

		function changeProperties(clazz) {
			var properties = [];
			
			var classes = [];
			classes.push(clazz);
			
			while (classes.length > 0) {
				var clazz = classes.shift();
				if (clazz.properties != undefined) {
					for (var i in clazz.properties) {
						var propertyName = clazz.properties[i];
						if (properties.indexOf(propertyName) == -1) {
							properties.push(propertyName);
						}
					}
				}
				if (clazz.parents != undefined) {
					classes = classes.concat(clazz.parents);
				}
			}
			
			properties.sort();
				
			Y.one('#class-properties').setContent('');
			var frag = '<table>';
			for (var i in properties) {
				var property = properties[i];
				
				frag = frag + '<tr><td>' + property
						+ '</td><td><input class="class-property yui3-hastooltip" type="text" title="' + /* TODO */'" name="'
						+ property + '"/></td></tr>';
			}
			frag = frag + '</table>';
			Y.one('#class-properties').append(Y.Node.create(frag)).show();
		}
		
		function updateSearchBoxes(paramsMap) {
			if (paramsMap['query'] != undefined) {
				Y.one("#ac-input").set('value', paramsMap['query']);
			} else {
				Y.one("#ac-input").set('value', '');
			}
			if (paramsMap['pageSize'] != undefined) {
				Y.one("#numresults").set('value', paramsMap['pageSize']);
			}
			if (paramsMap['deref'] != undefined) {
				Y.one("#dereference").set('checked', paramsMap['deref'] == 'true');
			}
			if (paramsMap['index'] != undefined) {
				var selectOption;
				Y.one("#dataset").get("options").each( function() {
					if (this.get('value') == paramsMap['index']) {
						selectOption = this;
					}
				});
				
				if (selectOption == undefined) {
					alert("The given index named " + paramsMap['index'] + " was not found on the server.");
				} else {
					if (!selectOption.get('selected')) {
						// Change dataset.
						selectOption.set('selected', true);
					}
				}
			}
		}
		
		// The ajax query requests are driven through the history object.
		// We listen for changes and update the results as appropriated.
		history.on('change', function (e) {
			if (e.src === Y.HistoryHash.SRC_HASH) {
				updateSearchBoxes(e.newVal);
			}
			doQuery(e.newVal); 
		});
		
		var dataSetListConfig = {
			on: {
				success: function(transactionid, response, arguments) {
					var dataSetNames = Y.JSON.parse(response.response);
					for (var i in dataSetNames) {
						var dataSetName = dataSetNames[i];
						Y.one("#dataset").append('<option value=\"' + dataSetName + '\">' + dataSetName + '</option>');
					}
					
					updateSearchBoxes(history.get());
					
					initDataSet();
					Y.one('#dataset').on('change', initDataSet);
					
					doQuery(history.get());
				},
				failure: function(transactionid, response, arguments) {
					alert("Failed to load data set list.");
				}
			}
		}
		
		Y.io('ajax/dataSetList', dataSetListConfig);

		// On submit, retrieve and display search results
		Y.one('#search-by-class').on('submit', executeSearchByClass);

		// On submit, retrieve and display search results
		Y.one('#unifiedsearchform').on('submit', executeUnifiedSearch);

		var tabview = new Y.TabView({
			srcNode : '#resultContainer'
		});
		tabview.render();
	}
);
