; Copyright 2009 Howard M. Lewis Ship
;
; Licensed under the Apache License, Version 2.0 (the "License");
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;    http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
; implied. See the License for the specific language governing permissions
; and limitations under the License.

; Uses a SAX parser to tokenize an XML document into
; a stream of token structures.

(ns com.howardlewisship.cascade.internal.xmltokenizer
  (:use
   clojure.contrib.duck-streams)
  (:import
   (java.io BufferedReader)
   (org.xml.sax ContentHandler Attributes Locator InputSource XMLReader)
   (org.xml.sax.ext DefaultHandler2)
   (org.xml.sax.helpers XMLReaderFactory)
   (clojure.lang Keyword)))


(defstruct start-element-token :type :location :ns-uri :tag)
(defstruct attribute-token :type :location :ns-uri :name :value)
(defstruct begin-ns-prefix-token :type :location :ns-uri :ns-prefix)
(defstruct end-ns-prefix-token :type :location :ns-prefix)
(defstruct text-token :type :location :value)
(defstruct comment-token :type :location :value)
(defstruct end-element-token :type :location)

; The :location key of the above structs
(defstruct location :resource :line)

; tag names, namespace prefixes and attribute names are converted
; from strings into keywords

; Until there's a native (monadic?) XML parser, we use the SAX parser. Its model
; doesn't fit well into the functional world, so we have to have some thread-local mutable state.

(def *tokens*)
(def #^StringBuilder *buffer*)
(def *resource*)
(def *text-location*)
(def #^Locator *locator*)
(def *line*)
(def *location*)

(defn- to-keyword
  "Converts a string into a keyword."
  [string]
  (. Keyword (intern (symbol string))))

(defn- add-token
  "Adds a token to the *tokens* var."
  [token]
  (set! *tokens* (conj *tokens* token)))

(defn- current-location
  "Current location."
  []
  ; A little optimization here, to reuse *location* if the line number has not changed since the
  ; last call.
  (let [line (.getLineNumber *locator*)]
    (when (or (nil? *location*) (not= (*location* :line) line))
      (set! *location* (struct location *resource* line))))
  *location*)

(defn- add-attribute-tokens
  "Adds a token for each attribute."
  [#^Attributes attrs]
  (doseq [#^Integer x (range (.getLength attrs))]
    (let [uri (.getURI attrs x)
          name (.getLocalName attrs x)
          value (.getValue attrs x)
          token (struct attribute-token :attribute (current-location) uri (to-keyword name) value)]
      (add-token token))))


(defn- flush-text
  "Adds a text token if there's any text."
  []
  (when (pos? (.length *buffer*))
    (add-token (struct text-token :text *text-location* (.toString *buffer*)))
    (set! *text-location* nil)
    (.setLength *buffer* 0)))

(defn- add-text
  "Adds text to the buffer and manages the *text-location* var."
  [#^chars ch #^Integer start #^Integer length]
  (.append *buffer* ch start length)
  (when (nil? *text-location*)
    (set! *text-location* (current-location))))

(def #^DefaultHandler2 sax-handler
  (proxy
    [DefaultHandler2]
    []

    (startElement [uri local-name q-name #^Attributes attrs]
      (flush-text)
      (add-token (struct start-element-token :start-element (current-location) uri (to-keyword local-name)))
      (add-attribute-tokens attrs))

    (endElement [uri local-name q-name]
      (flush-text)
      (add-token (struct end-element-token :end-element (current-location))))

    (ignorableWhitespace [ch start length]
      (add-text ch start length))

    (characters [ch start length]
      (add-text ch start length))

    (startPrefixMapping [prefix uri]
      (flush-text)
      (add-token (struct begin-ns-prefix-token :begin-ns-prefix (current-location) uri prefix)))

    (endPrefixMapping [prefix]
      (flush-text)
      (add-token (struct end-ns-prefix-token :end-ns-prefix (current-location) prefix)))

    (comment [#^chars ch #^Integer start #^Integer length]
      (flush-text)
      (add-token (struct comment-token :comment (current-location)
                                       (String. ch start length))))

    ; This gets invoked once, early. The provided Locator is mutable.

    (setDocumentLocator [locator]
      (set! *locator* locator))))

(defn tokenize-xml
  "Parses an XML file using a standard source (file, path, URL, etc.) into a collection
  of xml-token struct-maps."
  [src]
  (binding [*tokens* []
            *buffer* (StringBuilder.)
            *resource* src
            *locator* nil
            *text-location* nil
            *line* nil
            *location* nil]
    (let [
      #^InputSource inputSource (InputSource. #^BufferedReader (reader src))
      #^XMLReader reader (XMLReaderFactory/createXMLReader)]
      (doto reader
        (.setContentHandler sax-handler)
        (.setProperty "http://xml.org/sax/properties/lexical-handler" sax-handler)
        (.parse inputSource))
      *tokens*)))