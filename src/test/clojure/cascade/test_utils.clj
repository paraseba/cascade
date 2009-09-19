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

(ns cascade.test-utils
  (:use
    (clojure (test :only [is are deftest]) )
    (clojure.contrib pprint duck-streams str-utils)
    cascade.config
    (cascade func-utils)
    (cascade.internal utils)))

(deftest classpath-resource-does-not-exist
  (is (= (find-classpath-resource "does/not/exist.txt") nil)))

(deftest classpath-resource-exists
  (let [path "cascade/internal/resource.txt"]
    (is (= (slurp* (find-classpath-resource path))
      (slurp (str "src/test/resources/" path))))))

(deftest namespace-relative
  (is (= (slurp* (find-classpath-resource 'namespace-relative "ns.txt"))
    (slurp "src/test/resources/cascade/test_utils/ns.txt"))))
    
(deftest to-str-list-conversions
  (is (= (to-str-list nil) "(none)"))
  (is (= (to-str-list []) "(none)"))
  (is (= (to-str-list ["fred"]) "fred"))
  (is (= (to-str-list ["fred" "barney" "wilma"]) "fred, barney, wilma")))    


(defn- test-chain
  [chains selector expected & params]
  (binding [configuration (atom { :chains chains})]
    (let [chain (create-chain selector)]
      (is (= (apply chain params) expected)))))

(defn- always
  [result]
  (fn [] result))

(deftest chain-to-function
  (test-chain { :test (always :goober) } :test :goober))
  
(deftest indirect-chain
  (test-chain { :test :indirect, :indirect (always :final) } :test :final))

(deftest nil-in-chain-ignored
  (test-chain { :test [ nil :indirect], :indirect (always :final) } :test :final))
  
(deftest sequence-of-functions-in-chain
  (test-chain { :test [ (always nil) (always :final)]} :test :final))
      
(deftest chain-with-multiple-params
  (let [combiner (fn [& args] (str-join ", " args))]
    (test-chain {:combiner combiner} :combiner
       "fred, barney" 
       "fred" "barney")))

(deftest pipeline
  (let [to-upper-filter (fn [delegate s] (delegate (.toUpperCase s)))
        doubler-filter (fn [delegate s] (format "before=%s after=%s" s (delegate s)))]
    (binding [configuration (atom {:filters {:upper to-upper-filter 
                                      :doubler doubler-filter 
                                      :default [:doubler :upper]}})]
      (create-pipeline :default identity)
      (is (= (call-pipeline :default "portland") "before=portland after=PORTLAND")))))

(deftest test-function?
  (is (= (function? map) true) "a real function")
  (is (= (function? nil) false) "nil is not a function")    
  (is (= (function? "string") false) "strings are not functions")
  (is (= (function? {}) true) "maps act as a function"))
  
(deftest test-to-seq
  (let [v [:a :b]]
    (is (identical? (to-seq v) v)))
  (is (= (to-seq :a) [:a])))
  
(deftest test-qualified-function-name
  (is (= (qualified-function-name #'map) "clojure.core/map"))
  (is (= (qualified-function-name #'create-pipeline) "cascade.internal.utils/create-pipeline")))  
  
