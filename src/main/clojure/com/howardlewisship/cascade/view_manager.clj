(ns com.howardlewisship.cascade.view-manager
  (:use
    clojure.contrib.str-utils
    com.howardlewisship.cascade.internal.utils
    com.howardlewisship.cascade.dom
    com.howardlewisship.cascade.config
    com.howardlewisship.cascade.internal.parser))

; OK, so now a fragment function takes two parameters: env and params.  A view function is simply a wrapper
; around a fragment function that takes just the env and supplys nil for the params.

; The URI for a fragment

(def fragment-uri "cascade")

(def fragment-cache (atom {}))
(def view-cache (atom {}))

(declare get-fragment)

; A render function takes a single parameter (the environment) and returns a seq
; of DOM nodes that can be transformed and then streamed. Any returned nil values
; are filtered out.

(defn- combine-render-funcs
  "Combines a number of render functions together to form a composite render function. "
  [funcs]
  (fn combined [env params]
    (remove nil?
      (apply concat
        (for [f funcs]
          (f env params))))))

(defn- construct-attributes
  "Convert attribute tokens into attribute DOM nodes."
  [attribute-tokens]
  (for [token attribute-tokens]
    (struct-map dom-node
      :type :attribute
      :ns-uri (token :ns-uri)
      :name (token :name)
      :value (token :value))))

(defn- create-render-body-fn
  "Creates a function that takes a single env parameter and invokes the provided function
  to render the body, using the params of the encloding/invoking fragment function."
  [body-combined container-params]
  (fn [env]
    (body-combined env container-params)))

(defmulti to-fragment-fn :type)

(defmethod to-fragment-fn :text
  [parsed-node]
  (fn [env params] [(struct-map dom-node :type :text :value (-> parsed-node :token :value))]))

(defmethod to-fragment-fn :element
  [parsed-node]
  ; TODO: handle elements in the cascade namespace specially
  ; TODO: check for cascade namespace attributes
  (let [body (parsed-node :body)
        token (parsed-node :token)
        element-uri (token :ns-uri)
        element-name (token :tag)
        element-ns-uri-to-prefix (parsed-node :ns-uri-to-prefix)
        body-as-funcs (map to-fragment-fn body)
        body-combined (combine-render-funcs body-as-funcs)
        attributes (construct-attributes (parsed-node :attributes))]
    (if (= fragment-uri element-uri)
      (fn fragment-renderer [env params]
        ; TODO: Error if a fragment element defines any namespace besides cascade.
        (let [frag-func (get-fragment (name element-name))
              inner-params [] ; TODO: evaluate parameters
              body-renderer (create-render-body-fn body-combined params)
              ; TODO: rebuild token, stripping from :attributes any parameters
              frag-env (merge env {:element-token token
                                   :render-body body-renderer})]
          (frag-func frag-env inner-params)))
      ; otherwise, a static element node
      ; TODO: handle dynamic attributes
      (fn element-node-renderer [env params]
        [(struct-map dom-node
          :type :element
          :ns-uri element-uri
          :ns-uri-to-prefix element-ns-uri-to-prefix
          :name element-name
          ; currently assuming that attributes are "static" but
          ; that will change ... though we should seperate "static" from "dynamic"
          :attributes attributes
          ; TODO: there might be a way to identify that a static element has only static
          ; content, in which case the body can itself be computed statically
          :content (body-combined env params))]))))


(defn parse-and-create-fragment
  "Parses a source file and creates a fragment function from it."
  [src]
  (let [root-node (parse-template src)]
    (to-fragment-fn root-node)))

(defn parse-and-create-view
  "Parses a source file and creates a view function from it."
  [src]
  (let [frag-func (parse-and-create-fragment src)]
    (fn [env] (frag-func env nil))))

(defn- search-namespaces
  [name namespaces]
  (let [func-ref (first-non-nil (map #(ns-resolve % name) namespaces))]
    (and func-ref (deref func-ref))))

(defn- create-from-template
  "Searches for a template file as a classpath resource in one of the namespaces, creating a function (using the factory)
  if found. If not found, throws RuntimeException."
  [name namespaces factory-fn]
  (let [path (str name ".cml")
        match (first-non-nil (map #(find-namespace-resource % path) namespaces))]

    (if (nil? match)
      (throw (RuntimeException. (format "Could not locate template '%s' in any of namespaces %s."
        path
        (str-join ", " (map ns-name namespaces))))))

    ; TODO: Add lots of meta-data to the created function.

    (factory-fn match)))

(defn- find-or-create-fn
  "Searches for an existing function in any of the namespaces, or creates a function from a bare template."
  [name namespaces factory-fn]
  (or
    (search-namespaces name namespaces)
    (create-from-template name namespaces factory-fn)))

(defn- get-fn
  "Gets a view or fragment function."
  [#^String name cache config-key factory-fn]
  (let [existing (get @cache name)]
    (or
      existing
      (let [namespaces (get configuration config-key)
            created (find-or-create-fn name namespaces factory-fn)]
        (swap! cache assoc name created)
        created))))

(defn get-fragment
  "Gets a fragment function with a given name. Fragment functions expect an env and a params and return a seq of render nodes."
  [#^String name]
  (get-fn name fragment-cache :fragment-namespaces parse-and-create-fragment))

(defn get-view
  "Gets a fragment function with a given name. View functions expect an env and return a seq of render nodes."
  [#^String name]
  (get-fn name view-cache :view-namespaces parse-and-create-view))