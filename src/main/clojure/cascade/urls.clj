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

(ns #^{:doc "Functions used when encoding data into portions of a URL."} 
  cascade.urls
  (:import (clojure.lang Keyword Symbol))
  (:require
    (clojure.contrib [str-utils2 :as s2]))    
  (:use (cascade config fail)
        (cascade.internal utils)))
  
(defmulti to-url-string
  "Used to encode a value for inclusion as a query parameter value, or as extra path data.
  Dispatches on the class of the single parameter."
  class)
  
;; TODO: Encode quotes and such inside the string.  
(defmethod to-url-string String [s] s)
(defmethod to-url-string Number [#^Number n] (.toString n))

;;  Assumes that keyword and symbols names are already URL safe
(defmethod to-url-string Keyword [kw] (name kw))
(defmethod to-url-string Symbol [sym] (name sym))

(defn parse-int [s] (Integer/parseInt s))

(doseq [[k f] [[:int #'parse-int] 
               [:str #'str]]]
  (assoc-in-config [:url-parser k] f))


(defn get-parse-fn [parser]
  (if (function? parser)
    parser
    (let [parse-fn (read-config [:url-parser parser])]
      (fail-if (nil? parse-fn) "%s not found inside configuration :url-parser." parser)
      parse-fn)))
      
(defn parse-url-value
  "Used internally to parse a URL value. Nil values stay nil, but others are subject to exceptions
  if improperly formed. Parser will either be a keyword (looked for in the :url-parser configuration) or a function."
  [value parser]
  (let [parse-fn (get-parse-fn parser)]
    (if (nil? value) value (parse-fn value))))

(defn parse-extra-path
  "Used internally to parse a positional value from the extra path in the URL."
  [extra-path index parser]
  (parse-url-value (get extra-path index) parser))
    

(defmacro parse-url
  "Handles the extraction and conversion of data encoded into the URL as extra path information or as query parameters."
  [env-symbol & symbol-mappings]
  (fail-unless (even? (count symbol-mappings)) "parse-url requires an even number of symbol mappings.")
  nil)

(defn split-path
  "Splits path (a string) on slash characters, returning a vector of the results. Leading slashes and doubled slashes are
  ignored (that is, empty names in the result are removed)."
  [#^String path]
  (let [names (.split #"/" path)]
    (vec (remove blank? (seq names)))))

(defn add-query-parameter
  [#^StringBuilder builder key value]
  (doto builder
    (.append (to-url-string key))
    (.append "=")
    (.append (to-url-string value))))

(defn construct-absolute-path
  "Converts a link map into a absolute path, including query parameters."  
  [context-path link-map]
  (loop [#^StringBuilder sb (doto (StringBuilder.)
                              (.append context-path)
                              (.append "/")
                              (.append (link-map :path)))
         sep "?"
         param-pairs (-> link-map :parameters)]
    (if (empty? param-pairs)
      (.toString sb)
      (do
        (.append sb sep)
        (add-query-parameter sb ((first param-pairs) 0) ((first param-pairs) 1))
        (recur sb "&" (next param-pairs))))))          

(defn link-map-from-path
  "Constructs a link map a path, extra path info, and optional query parameters. Applications should
  use the link-map macro instead."
  [fn-path extra-path-info query-parameters]
  {
    :path (if (empty? extra-path-info)
            fn-path
            (str fn-path "/" (s2/join "/" (map str extra-path-info))))
    :parameters query-parameters
  })           