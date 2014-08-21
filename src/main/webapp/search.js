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
	return (this.match("^" + str) == str);
};

// IE doesn't have indexOf method on Arrays.
if (!Array.prototype.indexOf) { 
    Array.prototype.indexOf = function(obj, start) {
         for (var i = (start || 0), j = this.length; i < j; i++) {
             if (this[i] === obj) { return i; }
         }
         return -1;
    }
}

var stats;
var fieldShortNames;
var fieldLongNames;
var store = {};
var webapp = "";
var RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
var contextsToColour = []; // TODO populate from server.

function unescape(s) {
	return decodeURIComponent(s.replace(/\+/g, ' '));
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
}

var argString = window.location.href.split('?')[1];
var args = []; // URL arguments
if (argString !== undefined) {
	args = parse(argString);
}

function getLocalName(uri) {
	if (uri.indexOf('#') > 0) {
		return uri.substring(uri.lastIndexOf('#') + 1);
	} else if (uri.indexOf('/') > 0) {
		return uri.substring(uri.lastIndexOf('/') + 1);
	} else {
		return uri;
	}
}

function getProviderName(sstring) {
	if (contextsToColour[sstring] === undefined) {
		return sstring;
	} else {
		return contextsToColour[sstring];
	}
}


function stripVersion(uri) {
	return uri.replace(/[0-9]+\.[0-9]+\.[0-9]+\//, "");
}

function encode(uri) {
	return uri.replace(/[^a-zA-Z0-9]+/g, "_");
}

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
	'escape',
	'querystring-parse-simple',
	'autocomplete',
	'autocomplete-filters',
	'autocomplete-highlighters',
	'yui-pager',
	
	function(Y) {
		Y.HistoryHash.hashPrefix = '!';
		var history = new Y.HistoryHash();
		
		var resultsPager = new Y.Pager();
		
		var ontologySelectedClass;

		function initDataSet() {
			fieldShortNames = [ "any" ];
			fieldLongNames = [ "any" ];
			Y.one("#results").setContent("");
			Y.one("#result-stats").setContent("");
			Y.one("#search-by-class-classes").setContent("");

			Y.one("#statistics-tree").setContent("");
			Y.one("#statistics-loader").show();

			var indexStatisticsConfig = {
				data: {
					'index': Y.one("#dataset").get('value')
				},
				on: {
					success : function(transactionid, response, args) {
						stats = Y.JSON.parse(response.responseText);

						Y.one("#class-loader").hide();
						Y.one("#class-message").setContent("");

						Y.one("#statistics-loader").hide();

						var autocompletefields = [ 'OR' ];
						
						for (var key in stats.fields) {
							if (stats.fields.hasOwnProperty(key)) {
								fieldShortNames.push(key);
								fieldLongNames.push(stats.fields[key]);
								// fill up the autocomplete
								// suggestion box
								autocompletefields.push(key);
							}
						}

						Y.one('#ac-input').plug(Y.Plugin.AutoComplete, {
							resultFilters : 'phraseMatch',
							queryDelimiter : ' ',
							tabSelect : 'true',
							typeAhead : 'true',
							source : autocompletefields
						});

						function classSortOrderCompare(a,b) {
							return b.count - a.count;
						}

						function addClass(clazz) {
							var rt = {};
							
							if (clazz === undefined) {
								return rt;
							}
							
							var label = '<a href="' + Y.Escape.html(clazz.className) + '">' + Y.Escape.html(clazz.localName) + '<span class="item-count">' + renderNumber(clazz.count) + '</span>';
							
							label += '</a>';
							rt.label = label;
							
							if (clazz.children !== undefined) {
								// Sort childern nodes highest count first.
								clazz.children.sort(classSortOrderCompare);
								var children = [];
								for (var i in clazz.children) {
									if (clazz.children.hasOwnProperty(i)) {
										var childClass = clazz.children[i];
										children.push(addClass(childClass));
									}
								}
								rt.type = 'TreeView';
								rt.children = children;
							}
							return rt;
						}
						
						// Display class selection dropdown
						if (stats.classes === undefined) {
							// Render TreeView
							Y.one('#statistics-tree').setContent('<p>There are no rdf:type tuples in this dataset.</p>');
							
							// Hide Ontology Search box
							Y.one('#ontologysearch').hide();
							
						} else {
							// Set up classes and their properties for the Ontology-based search.
							var classesWithProperties = [];
							for (var className in stats.classes) {
								if (stats.classes.hasOwnProperty(className)) {
									var clazz = stats.classes[className];
									// Add className field to each class.
									clazz.className = className;
									
									// Change all children class names to references +
									// for all classes that are children add a ref to the parent classes.
									for (var childIndex in clazz.children) {
										if (clazz.children.hasOwnProperty(childIndex)) {
											var childName = clazz.children[childIndex];
											var childClass = stats.classes[childName];
											clazz.children[childIndex] = childClass;
											if (childClass.parents === undefined) {
												childClass.parents = [];
											}
											childClass.parents.push(clazz);
										}
									}
									
									// get classes with properties.
									if (clazz.properties !== undefined) {
										classesWithProperties.push(clazz);
									}
								}
							}
							
							// Sort by the local name
							classesWithProperties.sort(function(a,b) {
								if ( a.localName < b.localName ) {
									return -1;
								}
								if ( a.localName > b.localName ) {
									return 1;
								}
								return 0;
							});
							
							var frag = '<select id="search-by-class-classes-select">';
							for (var classesWithPropertiesI = 0 ; classesWithPropertiesI < classesWithProperties.length ; classesWithPropertiesI++) {
								var classWithProp = classesWithProperties[classesWithPropertiesI];
								frag = frag + "<option value='" + classesWithPropertiesI + "'>" + Y.Escape.html(classWithProp.localName) + " - " + Y.Escape.html(classWithProp.className) + "</option>";
							}
							frag = frag + '</select>';
							var fragNode = Y.Node.create(frag);
							Y.one('#search-by-class-classes').append("I'm looking for a").append(fragNode).append("where");
							
							changeProperties(classesWithProperties[0]);
							fragNode.on('change', function(e) {
								i = e.target.get('value');
								changeProperties(classesWithProperties[i]);
							});
	
							// The right TreeView
							var rootClasses = [];
							for (var i in stats.rootClasses) {
								if (stats.rootClasses.hasOwnProperty(i)) {
									var rootClassName = stats.rootClasses[i];
									rootClasses.push(stats.classes[rootClassName]);
								}
							}

							// Sort roots highest count first.
							rootClasses.sort(classSortOrderCompare);
							
							var tree = [];
							for (var rootClassKey in rootClasses) {
								if (rootClasses.hasOwnProperty(rootClassKey)) {
									tree.push(addClass(rootClasses[rootClassKey]));
								}
							}
							
							Y.one('#statistics-tree').setContent('<ul id="statisticsTreeList"></ul>');
							var treeview = new Y.TreeView({
								toggleOnLabelClick : false,
								srcNode : '#statisticsTreeList',
								contentBox : null,
								type : "TreeView",
								children : tree
							});
	
							treeview.render();
							
							treeview.on("click", function (e){
								var anchor = e.details[0].domEvent.target;
								var typeUrl = anchor.getAttribute('href');
								if (typeUrl !== undefined) {
									var typeClass = stats.classes[typeUrl];
									if (typeClass !== undefined && typeClass.count > 0) {
										executeSearchByType(typeUrl);
									}
								}
							}); 
							
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

						
						// Only do the URL's search once the stats are loaded.
						doQuery(history.get());
					},
					failure : function(transactionid, response, args) {
						Y.one("#statistics-loader").hide();
						alert("Failed to get index stats from server. " + response);
					}
				}
			};
			
			Y.io('ajax/indexStatistics', indexStatisticsConfig);
		}

		function executeUnifiedSearch(e) {
			var query = Y.one("#ac-input").get('value');
			loadResults(query);
		}
		
		function executeSearchByQuery(query) {
			Y.one("#ac-input").set('value', query);
			executeUnifiedSearch(null);
		}
		
		function getDocumentByIdOrSubject(idOrSubject) {
			executeSearchByQuery('doc:' + idOrSubject);
		}
		function executeSearchByType(type) {
			executeSearchByQuery('type:{' + type + '}');
		}

		function executeSearchByClass(e) {
			var query = 'type:{' + ontologySelectedClass.className + '}';
			var params = Y.all(".search-by-class-property").each(function(thisNode) {
				if (thisNode.get('value') !== '') {
					query += ' (predicate:{' + thisNode.get('name') + '} ^ object:' + thisNode.get('value') + ')';
				}
			});
			
			executeSearchByQuery(query);
		}
		
		function pagerPage(targetPage, pagerState) {
			var current = history.get();
			current.pageStart = (targetPage - 1 ) * pagerState.pageSize;
			history.add(current);
		}

		function loadResults(query) {
			history.add({
				'index': Y.one("#dataset").get('value'),
				'query': query,
				'deref': Y.one("#dereference").get('checked'),
				'pageSize': Y.one("#numresults").get('value'),
				'pageStart' : 0
			});
		}

		function doQuery(paramsMap) {
			Y.one("#results-container").hide();
			if (paramsMap.index === undefined || paramsMap.query === undefined) { // || paramsMap.query.length == 0) {
				Y.one("#results-loader").hide();
				return;
			}
			
			Y.one("#results-loader").show();
			
			var doQueryConfig = {
				data: paramsMap,
				on: {
					success : function(transactionid, response, args) {
						var result = Y.JSON.parse(response.responseText);
						
						Y.one("#results-loader").hide();
						Y.one("#result-stats").setContent("Found " + renderNumber(result.numResults) + " results in " + result.time + " ms.");

						var ol = Y.Node.create("<ol></ol>");
						ol.setAttribute("start", result.pageStart + 1);

						var markers = [];
						for (var i in result.resultItems) {
							if (result.resultItems.hasOwnProperty(i)) {
								ol.append(renderResult(result.resultItems[i]));
							}
						}

						Y.one("#results").setContent("").append(ol);
						
						resultsPager.setState({
							pageSize: result.pageSize,
							items: result.numResults,
							page: 1 + Math.floor(result.pageStart / result.pageSize)
						});
						Y.one("#results-container").show();
					},
					failure : function(transactionid, response, args) {
						var message = "";
						if (response !== undefined) {
							message = Y.JSON.parse(response.responseText);
						}
						alert("Failed to get results from server. " + message);
						Y.one("#results-loader").hide();
					}
				}
			};
			Y.io('ajax/query', doQueryConfig);
		}

		function renderResult(result) {
			var li = Y.Node.create("<li class=\"result\"></li>");

			function predSort(a, b) {
				return (fieldLongNames.indexOf(encode(a.predicate)) - fieldLongNames.indexOf(encode(b.predicate)));
			}
			
			result.relations.sort(predSort);
			if (result.hasOwnProperty("label") && result.label !== undefined) {
				li.append('<span class="label">' + Y.Escape.html(result.label) + '</span>');
			}
			li.append("<br/>");

			// Group the relations by predicate
			var map = [];
			var types = [];

			if (result.hasOwnProperty("relations")) {
				for (var relationIndex in result.relations) {
					if (result.relations.hasOwnProperty(relationIndex)) {
						var relation = result.relations[relationIndex];
						if (map[relation.predicate] === undefined) {
							map[relation.predicate] = [ relation ];
						} else {
							map[relation.predicate].push(relation);
						}
						if (relation.predicate == RDF_TYPE) {
							var stripedType = stripVersion(relation.object);
							if (types.indexOf(stripedType) == -1) {
								types.push(stripedType);
							}
						}
					}
				}
			}
			
			function typeSort(a, b) {
				if (stats.classes == undefined) {
					return 0;
				}
				var countA = 0;
				// For very long documents, there is a small chance that the class will existing in the document but not be in the list of classes.
				if (stats.classes[a] !== undefined) {
					countA = stats.classes[a].count;
				}
				var countB = 0;
				if (stats.classes[b] !== undefined) {
					countB = stats.classes[b].count;
				}
				
				return countA - countB;
			}
			
			if (types.length > 0) {
				types.sort(typeSort);
				
				for (var type in types) {
					if (types.hasOwnProperty(type)) {
						li.append('&nbsp;<a class="type" href="' + Y.Escape.html(types[type]) + '">' + Y.Escape.html(getLocalName(types[type])) + '</a>&nbsp;');
					}
				}
				li.append('-&nbsp;');
			}

			var span;
			if (result.subject.match("^https?://")) {
				span = Y.Node.create('<span class="id"><a href=' + Y.Escape.html(result.subject) + '>' + Y.Escape.html(result.subject) + '</a></span>');
			} else {
				span = Y.Node.create('<span class="id">' + Y.Escape.html(result.subject) + '</span>');
			}
			if (result.subjectId !== undefined) {
				appendDocLink(span, result.subjectId);
			}
			li.append(span);

			var table = Y.Node.create('<table class=\"result\"></table>');
			table.appendChild(Y.Node.create('<colgroup><col class=\"predicate-col\"/><col class=\"value-col\"/></colgroup>'));
			var tbody = Y.Node.create('<tbody></tbody');
			table.appendChild(tbody);
			
			tbody.appendChild('<tr><th class="result-header">Property</th><th class=\"result-header\">Value</th></tr>');
			
			var i = 0;
			for (var predicate in map) {
				if (map.hasOwnProperty(predicate)) {
					if (predicate == RDF_TYPE) {
						continue;
					}
					
					var tdPredicate = Y.Node.create('<td class="predicate">' + Y.Escape.html(getLocalName(predicate)) + '</td>');
					
					var tdValues = Y.Node.create('<td class="object"></td>');
					for (var relationKey in map[predicate]) {
						if (map[predicate].hasOwnProperty(relationKey)) {
							var item = map[predicate][relationKey];
							var providedName = "unknown";
							if (item.context !== undefined) {
								providedName = getProviderName(item.context);
							}
							var div = Y.Node.create('<div title="' + providedName + '" class="source-' + providedName + '"></div>');
							div.appendChild(renderValue(item.object, item.label));
							if (item.subjectIdOfObject !== undefined) {
								appendDocLink(div, item.subjectIdOfObject);
							}
							tdValues.appendChild(div);
						}
					}
					
					var tr;
					if (i++ % 2 === 0) {
						tr = Y.Node.create('<tr class="even"></tr>');
					} else {
						tr = Y.Node.create('<tr class="odd"></tr>');
					}
					tr.appendChild(tdPredicate);
					tr.appendChild(tdValues);
					tbody.appendChild(tr);
				}
			}

			li.append(table);
			return li;
		}

		function appendDocLink(parent, docId) {
			var func = getDocumentByIdOrSubject;
			var param = docId;
			var closure = function() {
				func(param);
			};
			var element = Y.Node.create('<span class="doc-link">&nbsp;-&gt;</span>');
			element.on('click', closure);
			parent.appendChild(element);
		}
		
		// A value (URI or Literal) to be rendered. URI optionally
		// has anchortext
		function renderValue(ref, value) {
			if (ref.startsWith("urn:uuid:")) {
				ref = ref.substring(9);
				if (value === undefined) {
					value = ref;
				}
				var kbname = Y.one("#dataset").get('value');
				return '<a href="search.html?index=' + kbname + '&subject=' + Y.Escape.html(ref) + '">' + Y.Escape.html(value) + '</a>';
			}

			if (value === undefined) {
				value = ref;
			}
			if (ref.startsWith("http:")) {
				return '<a href="' + Y.Escape.html(ref) + '">' + Y.Escape.html(value) + '</a>';
			}
			return Y.Escape.html(value);
		}

		function changeProperties(clazz) {
			ontologySelectedClass = clazz;
			
			var properties = [];
			
			var classes = [];
			classes.push(clazz);
			
			var i;
			
			while (classes.length > 0) {
				clazz = classes.shift();
				if (clazz.properties !== undefined) {
					for (i in clazz.properties) {
						if (clazz.properties.hasOwnProperty(i)) {
							var propertyName = clazz.properties[i];
							if (properties.indexOf(propertyName) == -1) {
								properties.push(propertyName);
							}
						}
					}
				}
				if (clazz.parents !== undefined) {
					classes = classes.concat(clazz.parents);
				}
			}
			
			properties.sort();
				
			Y.one('#search-by-class-properties').setContent('');
			var tableNode = Y.Node.create('<table></table>');
			for (var propertiesI = 0 ; propertiesI < properties.length ; propertiesI++) {
					var property = properties[propertiesI];
					tableNode.append('<tr><td>' + property + '</td><td><input class="search-by-class-property" type="text" name="' + property + '"/></td></tr>');
			}
			Y.one('#search-by-class-properties').append(tableNode).show();
		}
		
		function updateSearchBoxes(paramsMap) {
			if (paramsMap.query !== undefined) {
				Y.one("#ac-input").set('value', paramsMap.query);
			} else {
				Y.one("#ac-input").set('value', '');
			}
			if (paramsMap.pageSize !== undefined) {
				Y.one("#numresults").set('value', paramsMap.pageSize);
			}
			if (paramsMap.deref !== undefined) {
				Y.one("#dereference").set('checked', paramsMap.deref == 'true');
			}
			if (paramsMap.index !== undefined) {
				var selectOption;
				Y.one("#dataset").get("options").each( function() {
					if (this.get('value') == paramsMap.index) {
						selectOption = this;
					}
				});
				
				if (selectOption === undefined) {
					alert("The given index named " + paramsMap.index + " was not found on the server.");
				} else {
					if (!selectOption.get('selected')) {
						// Change dataset.
						selectOption.set('selected', true);
					}
				}
			}
		}
		
		function renderNumber(number) {
			if (number >= 1000000000) {
				return "~" + Math.floor(number / 1000000000) + " billion";
			} else if (number >= 1000000) {
				return "~" + Math.floor(number / 1000000) + " million";
			} else if (number >= 1000) {
				return "~" + Math.floor(number / 1000) + " thousand";
			}
			return number;
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
				success: function(transactionid, response, args) {
					var dataSetNames = Y.JSON.parse(response.responseText);
					for (var i in dataSetNames) {
						if (dataSetNames.hasOwnProperty(i)) {
							var dataSetName = dataSetNames[i];
							Y.one("#dataset").append('<option value=\"' + dataSetName + '\">' + dataSetName + '</option>');
						}
					}
					
					updateSearchBoxes(history.get());
					
					initDataSet();
					Y.one('#dataset').on('change', initDataSet);
				},
				failure: function(transactionid, response, args) {
					alert("Failed to load data set list.");
				}
			}
		};
		
		Y.io('ajax/dataSetList', dataSetListConfig);

		// On submit, retrieve and display search results
		Y.one('#search-by-class').on('submit', executeSearchByClass);

		// On submit, retrieve and display search results
		Y.one('#unifiedsearchform').on('submit', executeUnifiedSearch);

		resultsPager.setCallback(pagerPage);
		resultsPager.setNumberFormatter(renderNumber);
		resultsPager.setState({
			pagerElements: ['#results-pager-top', '#results-pager-bottom'],
			statusElements: ['#results-pager-top-status']
		});
	}
);
