{{/* ------------------------------------------------------------------ names and labels */}}

{{- define "wp.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "wp.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{/* Resource name of a component. Usage: include "wp.componentName" (dict "root" $ "name" "api-gateway") */}}
{{- define "wp.componentName" -}}
{{- printf "%s-%s" (include "wp.fullname" .root) .name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "wp.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
{{- default (include "wp.fullname" .) .Values.serviceAccount.name -}}
{{- else -}}
{{- default "default" .Values.serviceAccount.name -}}
{{- end -}}
{{- end -}}

{{- define "wp.labels" -}}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
app.kubernetes.io/name: {{ include "wp.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: workflow-platform
{{- end -}}

{{/* Usage: include "wp.selectorLabels" (dict "root" $ "name" "api-gateway") */}}
{{- define "wp.selectorLabels" -}}
app.kubernetes.io/name: {{ include "wp.name" .root }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/component: {{ .name }}
{{- end -}}

{{- define "wp.componentLabels" -}}
{{ include "wp.labels" .root }}
app.kubernetes.io/component: {{ .name }}
{{- end -}}

{{/* Value of `key` from the component, falling back to .Values.defaults (whole-key replacement), as YAML.
Unset or null renders "{}" so map settings can be disabled with `key: null`. */}}
{{- define "wp.setting" -}}
{{- $v := dict -}}
{{- if hasKey .c .key -}}
{{- $v = index .c .key -}}
{{- else if hasKey .root.Values.defaults .key -}}
{{- $v = index .root.Values.defaults .key -}}
{{- end -}}
{{- if kindIs "invalid" $v -}}
{}
{{- else -}}
{{- toYaml $v -}}
{{- end -}}
{{- end -}}

{{- define "wp.image" -}}
{{- $img := .c.image | default dict -}}
{{- $registry := $img.registry | default .root.Values.image.registry -}}
{{- $tag := $img.tag | default .root.Values.image.tag | default .root.Chart.AppVersion -}}
{{- if $registry -}}
{{- printf "%s/%s:%s" $registry $img.repository $tag -}}
{{- else -}}
{{- printf "%s:%s" $img.repository $tag -}}
{{- end -}}
{{- end -}}

{{/* ------------------------------------------------------------------ reusable workload: Deployment + Service
Usage: include "wp.workload" (dict "root" $ "name" "api-gateway" "c" $component)
*/}}
{{- define "wp.workload" -}}
{{- $root := .root -}}
{{- $name := .name -}}
{{- $c := .c -}}
{{- $d := $root.Values.defaults -}}
{{- $fullname := include "wp.componentName" . -}}
{{- $hpa := $c.hpa | default dict -}}
{{- $antiAffinity := fromYaml (include "wp.setting" (dict "root" $root "c" $c "key" "podAntiAffinity")) -}}
{{- $podAnnotations := fromYaml (include "wp.setting" (dict "root" $root "c" $c "key" "podAnnotations")) -}}
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ $fullname }}
  labels:
    {{- include "wp.componentLabels" . | nindent 4 }}
spec:
  {{- if not $hpa.enabled }}
  replicas: {{ include "wp.setting" (dict "root" $root "c" $c "key" "replicas") }}
  {{- end }}
  revisionHistoryLimit: 5
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 0
      maxSurge: 1
  selector:
    matchLabels:
      {{- include "wp.selectorLabels" . | nindent 6 }}
  template:
    metadata:
      labels:
        {{- include "wp.componentLabels" . | nindent 8 }}
      annotations:
        {{- if $c.springBoot }}
        checksum/config: {{ include (print $root.Template.BasePath "/configmap.yaml") $root | sha256sum }}
        prometheus.io/path: /actuator/prometheus
        prometheus.io/port: {{ $c.port | quote }}
        {{- end }}
        {{- with $podAnnotations }}
        {{- toYaml . | nindent 8 }}
        {{- end }}
    spec:
      serviceAccountName: {{ include "wp.serviceAccountName" $root }}
      # The services never call the Kubernetes API.
      automountServiceAccountToken: false
      # Docker-link env vars (e.g. REDIS_PORT=tcp://10.0.0.1:6379 for a Service named "redis") would clash with
      # the variables the services read.
      enableServiceLinks: false
      {{- with $root.Values.imagePullSecrets }}
      imagePullSecrets:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      terminationGracePeriodSeconds: {{ include "wp.setting" (dict "root" $root "c" $c "key" "terminationGracePeriodSeconds") }}
      securityContext:
        {{- include "wp.setting" (dict "root" $root "c" $c "key" "podSecurityContext") | nindent 8 }}
      {{- with $antiAffinity }}
      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
            - weight: {{ .weight }}
              podAffinityTerm:
                topologyKey: {{ .topologyKey }}
                labelSelector:
                  matchLabels:
                    {{- include "wp.selectorLabels" (dict "root" $root "name" $name) | nindent 20 }}
      {{- end }}
      {{- with fromYaml (include "wp.setting" (dict "root" $root "c" $c "key" "nodeSelector")) }}
      nodeSelector:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      {{- with fromYamlArray (include "wp.setting" (dict "root" $root "c" $c "key" "tolerations")) }}
      tolerations:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      containers:
        - name: {{ $name }}
          image: {{ include "wp.image" . | quote }}
          imagePullPolicy: {{ ($c.image | default dict).pullPolicy | default $root.Values.image.pullPolicy }}
          ports:
            - name: http
              containerPort: {{ $c.port }}
              protocol: TCP
          {{- if $c.springBoot }}
          envFrom:
            - configMapRef:
                name: {{ include "wp.fullname" $root }}-config
          {{- end }}
          env:
            - name: PORT
              value: {{ $c.port | quote }}
            {{- if $c.springBoot }}
            - name: JAVA_TOOL_OPTIONS
              value: {{ $c.javaToolOptions | default $d.javaToolOptions | quote }}
            {{- end }}
            {{- range $key, $value := $c.env }}
            - name: {{ $key }}
              value: {{ tpl (toString $value) $root | quote }}
            {{- end }}
            {{- range $c.secretEnv }}
            - name: {{ .name }}
              valueFrom:
                secretKeyRef:
                  name: {{ .existingSecret }}
                  key: {{ .key }}
            {{- end }}
          {{- $probes := $c.probes }}
          {{- if and (not $probes) $c.springBoot }}
          {{- $probes = $d.springProbes }}
          {{- end }}
          {{- with $probes }}
          {{- toYaml . | nindent 10 }}
          {{- end }}
          lifecycle:
            preStop:
              sleep:
                seconds: {{ include "wp.setting" (dict "root" $root "c" $c "key" "preStopSleepSeconds") }}
          resources:
            {{- include "wp.setting" (dict "root" $root "c" $c "key" "resources") | nindent 12 }}
          securityContext:
            {{- include "wp.setting" (dict "root" $root "c" $c "key" "containerSecurityContext") | nindent 12 }}
          volumeMounts:
            - name: tmp
              mountPath: /tmp
            {{- with $c.extraVolumeMounts }}
            {{- toYaml . | nindent 12 }}
            {{- end }}
      volumes:
        - name: tmp
          emptyDir:
            sizeLimit: {{ include "wp.setting" (dict "root" $root "c" $c "key" "tmpSizeLimit") }}
        {{- with $c.extraVolumes }}
        {{- toYaml . | nindent 8 }}
        {{- end }}
---
apiVersion: v1
kind: Service
metadata:
  name: {{ $fullname }}
  labels:
    {{- include "wp.componentLabels" . | nindent 4 }}
spec:
  type: {{ ($c.service | default dict).type | default "ClusterIP" }}
  selector:
    {{- include "wp.selectorLabels" . | nindent 4 }}
  ports:
    - name: http
      port: {{ $c.port }}
      targetPort: http
      protocol: TCP
      {{- with ($c.service | default dict).nodePort }}
      nodePort: {{ . }}
      {{- end }}
{{- end -}}
