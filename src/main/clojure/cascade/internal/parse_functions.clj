; Copyright 2009 Howard M. Lewis Ship
;
; Licensed under the Apache License, Version 2.0 (the "License");
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;   http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
; implied. See the License for the specific language governing permissions
; and limitations under the License.

(ns #^{:doc "A form parser used when building functions, to emulate (defn)'s ability to add documentation
  and other meta-data"}
  cascade.internal.parse-functions
  (:use (clojure.contrib monads)
        (cascade.internal utils parser)))
        
(def fn-def-parser
  (domonad parser-m
    [fn-name match-symbol
     doc-string (optional match-string)
     fn-meta-data (optional match-map)     
     parameters match-vector
     bindings (optional match-vector)
     fn-forms (one-or-more any-form)]
    (let [doc-meta (and doc-string {:doc doc-string})
          full-meta (merge ^fn-name fn-meta-data doc-meta)
          symbol-with-meta (with-meta fn-name full-meta)]
      [symbol-with-meta parameters bindings fn-forms])))


(defn parse-function-def
  "Parses a flexible set of forms consisting of an optional documention string, an optional meta-data map, a
  required vector of parameters, and a series of additional forms. Returns a vector of the name (with additional meta
  data from the documentation string provided meta-data map), the parameters vector, and a seq of the additional forms."
  [fn-def-forms]
  (run-parse fn-def-parser fn-def-forms "function definition"))
      
(def render-link-forms-parser
  (domonad parser-m
    [extra-path-info (optional match-vector)
     query-parameters (optional match-map)
     template-forms (one-or-more any-form)]
     [extra-path-info query-parameters template-forms]))
     
(defn parse-render-link-forms
  "Parses the extra forms used by the cascade/link-to macro, returning a seq of three values:
  a vector of extra path info, a map of query parameters, and a seq of template forms."
  [forms]
  (run-parse render-link-forms-parser forms "render-link"))
