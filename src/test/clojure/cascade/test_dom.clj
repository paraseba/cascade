; Copyright 2009 Howard M. Lewis Ship
;
; Licensed under the Apache License, Version 2.0 (the "License");
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;   http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
; implied. See the License for the specific language governing permissions
; and limitations under the License.

(ns cascade.test-dom
	(:import
		java.io.StringWriter)
	(:require
		(clojure (zip :as z)))
  (:use
		(clojure test)
		cascade
		(cascade dom)
		(cascade.internal viewbuilder)))

(defn render-as-string
	[dom-nodes]
	(let [out (StringWriter.)]
		(render-xml dom-nodes out)
		(.toString out)))
		
(defn render-node
	"Render a single node as a string."
	[root-node]
	(render-as-string [root-node]))

(defn zip-append-children
	[loc children]
	(reduce z/append-child loc children)) 
	
(deftest dom-zipping
	(let [start-node (first (template :html [ :head :body ]))
				new-node (-> (dom-zipper start-node)
										 z/down
										 (zip-append-children 
										 		(template 
										 			:script { :src "a.js" }
										 			:script { :src "b.js" }))
										 z/root)]
		(is (= (render-node start-node) "<html><head/><body/></html>"))
		(is (= (render-node new-node) "<html><head><script src=\"a.js\"/><script src=\"b.js\"/></head><body/></html>"))))

(deftest test-navigate-dom-path
	(let [root (first (template 
											:html [ 
												:head [ 
													:meta
													:script { :src "a.js" }
													:script { :src "b.js" }
													:title [ "For Navigation Test" ]
												]
												:body [
													"Floating literal text."
													:p [ "Cascade!" ]
													:p [ "Second paragraph" ]
												]]))
		    dz (dom-zipper root)]
		
		(are [path rendered-value]
			(is (= (render-node (z/node (navigate-dom-path dz path))) rendered-value))
			[:html :body :p] "<p>Cascade!</p>"
			[:html :head :title] "<title>For Navigation Test</title>")

			(is (nil? (navigate-dom-path dz [:not-html])))
			(is (nil? (navigate-dom-path dz [:html :not-found])))))
	
	
(deftest test-extend-dom
		; to simplify tests, not using attributes
		(let [new-nodes (template :script [ "a.js" ] :script [ "b.js" ])
					dom-nodes (template :html [ :head [ :script [ "x.js" ] :meta  [ "via cascade" ]] :body [:p "Cascade!" ]])]
					
			(are [path position expected-text]
				(is (= (render-as-string (extend-dom dom-nodes path position new-nodes)) expected-text))
				[:html :head] :top "<html><head><script>a.js</script><script>b.js</script><script>x.js</script><meta>via cascade</meta></head><body><p/>Cascade!</body></html>"
				[:html :head :meta] :before "<html><head><script>x.js</script><script>a.js</script><script>b.js</script><meta>via cascade</meta></head><body><p/>Cascade!</body></html>"
				[:html :head :script] :after "<html><head><script>x.js</script><script>a.js</script><script>b.js</script><meta>via cascade</meta></head><body><p/>Cascade!</body></html>"
				[:html :head] :bottom "<html><head><script>x.js</script><meta>via cascade</meta><script>a.js</script><script>b.js</script></head><body><p/>Cascade!</body></html>"
				[:html :not-found] :top "<html><head><script>x.js</script><meta>via cascade</meta></head><body><p/>Cascade!</body></html>")))				
		
(deftest test-encode-string
	(are [input output]
		(is (= (encode-string input) output))
		"one < 2" "one &lt; 2"
		"two > one" "two &gt; one"
		"Calvin & Hobbes" "Calvin &amp; Hobbes"
		(str "What's this: " (char 128) (char 31)) "What's this: &#x80;&#x1f;"))
		
(run-tests)