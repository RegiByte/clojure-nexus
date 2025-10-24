(ns transducers
  (:require
   [clojure.core.async :as async]
   [clojure.string :as str]))

;; Let's start with a framework of thinking
;; A reduction process (e.g reduce) is like
;; a (possibly infinite) conveyor belt of values
;; as the values pass through the belt
;; there are certain "machines" or "mechanisms"
;; that may want to perform modifications to the values
;; or filter them out of the belt
;; the machines at the factory know nothing about 
;; what is being handled in the belt
;; they only know what they've been told to do with the values
;; it is up to a higher level entity (dev) to define the rules by which the machines
;; will handle the values passing through them

;; So in a way it is like satisfactory/factorio
;; Where we have resources (values) that come through a belt
;; and they pass through a series of transformations
;; e.g they become other resources or get transformed 
;; they get tagged or filtered/moved to another conveyor belt
;; what happens to them prior/after the transformation in the machine
;; is of no concern to the machine itself

;; The "technical" way to see it is a like a higher order reducer
;; it's job is to wrap another reducing function and modify it's process a little bit

(def run-factory transduce)

(comment

  ;; Machine 1 is an announcer of work
  ;; It's job is to simply watch the production line and say
  ;; "hey, this is what we're working with today"
  ;; when the work is done it says "okay, this is what we got done today"
  (defn machine-1 [msg]
    (fn [next-machine] ; a.k.a reducing function
      (fn
        ([] (next-machine)) ; Init
        ([result]
         (println "we're done!" {:final-result result})
         (next-machine result)) ; Completion
        ([result new-input] ; Work
         ;; This machine doesn't do anything, it just says "hey this is passing through"
         (println msg {:result-so-far result
                       :new-input new-input})
         (next-machine result new-input)))))

  (run-factory (machine-1 "input coming through") conj [] [1 2 3])

  ;; This machine has two roles (for illustration)
  ;; it receives a predicate (when to act) and an operation (how to act)
  (defn machine-2 [pred op]
    (fn [next-machine] ; next machine in the conveyor belt
      (fn
        ([] (next-machine)) ; init
        ([result]
         (println "")
         (println "machine-2 is done" {:result result} "\n")
         (next-machine result)) ; Completion
        ([result new-input]
         (let [should-act (pred new-input)
               acted-upon (op new-input)]
           (println "passing through machine 2"
                    {:result-so-far result
                     :input new-input
                     :should-act should-act
                     :acted-upon acted-upon})
           (if should-act
             ; calls the next machine after acting on the value
             (next-machine result acted-upon)
             ; If we got instructions to not act on the value
             ; then we don't change anything, we stop here
             ; someone else will take care of it
             result))))))

  (run-factory (machine-2 #(odd? %) #(* 2 %))
               conj [] [1 2 3 4 5 6])

  ;; And finally, transducers are composable
  ;; So we can simply join them together
  ;; creating what can be thought of as a "pipeline of transformations"

  ;; Machine 3 is the combination of machine-1 and machine-2
  ;; so it will do everything in the pipeline for each item in it
  ;; it will announce the input coming through
  ;; filter the ones that we are not interested in
  ;; and return the ones that we are, after transforming them a little bit
  (def machine-3
    (comp (machine-1 "input coming through")
          (machine-2 #(odd? %) #(* 2 %))))

  (run-factory machine-3 conj [] [1 2 3 4 5 6 7 8 9 10])

  (map #(> 2 %))


  ;; And since transducers are composable
  ;; and the wrapping of a transducer returns another transducer
  ;; we can create another machine that composes the composition of a machine
  ;; hence machines 4 and 5

  ;; a completion machine, it says when we are done and what we did
  (defn machine-4 [msg]
    (fn [next-machine] ; a.k.a reducing function
      (fn
        ([] (next-machine)) ; Init
        ([result]
         (print msg {:final-result result})
         (next-machine result)) ; Completion
        ([result new-input] ; Work, nothing to do
         (next-machine result new-input)))))

  (def machine-5
    (comp machine-3
          (machine-4 "we are finally done, good work boys!")))

  (run-factory machine-5 conj [] [1 2 3 4 5 6 7 8 9 10])

  ;
  )


;;;; 


(comment
  (def story ["The" "quick" "brown" "fox" "jumps" "over" "the" "lazy" "dog"])

  (defn long-word? [^String word]
    (-> word
        .length
        (> 3)))

  (map str/upper-case (filter long-word? story))

  (defn logging [f]
    (fn [& args]
      (prn args)
      (apply f args)))

  (reduce (logging conj) [] story)

  ;; filter map
  (defn step [result-so-far input]
    (if (long-word? input)
      (conj result-so-far (str/upper-case input))
      result-so-far))

  (reduce step [] story)

  ; transducer version
  ; you just define the steps of computation
  ; and everything happens in a single pass through the data
  ; rather than multiple transformations on multiple intermediary collections
  (def composition (comp (filter long-word?)
                         (map str/upper-case)))

  (transduce composition conj [] story)

  ;
  )

(comment


  ((comp #(* 2 %) ; then this
         #(inc %) ; first this
         )1) ;-> 4 - if it were the other way around, the result would be 3

  ; transducers flip the order
  ; they wrap in reverse order but applicate the functions
  ; in order
  (def xf (comp (map inc) ; first this
                (filter even?) ; then this
                ))

  (sequence xf [1 2 3])
  (transduce xf conj [] [1 2 3 4 5])

  (def in (async/chan 10 xf))
  (async/onto-chan! in [1 2 3 4 5])
  (async/<!! (async/into [] in))

  (defn odd-square-xf [msg]
    (fn [rf] ;; This is the transducer
      (fn
        ([] (rf)) ; Init
        ([result] (rf result)) ; Completion
        ([result new-input] ; step
           ; whatever we're doing, let's log the message before yielding the new value
         (println msg {:result result
                       :new-input new-input
                       :should-transform? (odd? new-input)
                       :potential-result (* new-input new-input)})
         (if (odd? new-input)
           (rf result (* new-input new-input))
             ; This next part is just for visualization, usually you just return result
             ; meaning, you don't modify the result in any way
             ; in here we're "tagging" it, indicating no usage
             ; just to visualize the result
           (rf result (str new-input " is no good, I'm not touching it")))))))

  (transduce
   (odd-square-xf "Hi from transducer creator")
   conj [] [1 2 3 4 5 6 7])


  ; Composing
  (def composed-xf
    (comp (odd-square-xf "Hi from transducer creator")
          (filter #(or (string? %)
                       (< % 50)))))

  (transduce composed-xf conj [] [1 3 5 7 9 10])

    ;;;;



  (def xf (comp (map inc) (filter even?)))
  (def rf (xf conj))
  xf
  rf

  (rf [] 1)
  (rf [2] 2)
  (rf [2] 3)

  xf
  rf
  ;; then inspect rf — it's a fully composed reducing function

  (def pipeline
    (comp
     (map #(* % %))
     (filter #(< % 50))))

  (transduce pipeline conj [] [1 2 3 4 5 6 7 8])
  ;
  )