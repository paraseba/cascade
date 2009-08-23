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

; Script that loads all tests for execution.

(use 'clojure.test)

(set! *warn-on-reflection* true)

(def spaces (map #(symbol (str "cascade." %)) [
  "test-utils" "test-config" "test-cascade" "test-parse-functions" "test-path-map"]))

(println "Loading code ...")
(time (apply use spaces))
  
(println "Executing tests ...")

(doseq [ns (map find-ns spaces)]
  (time (run-tests ns)))
