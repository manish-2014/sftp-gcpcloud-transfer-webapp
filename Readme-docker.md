# Docker Read me

# To bulid Docker container for GCP GCR

1. make sure maven compilation and package works without error

	```
		
		mvn clean package -Dmaven.test.skip=true
	```

2. Docker command:
```
	docker build -t us-central1-docker.pkg.dev/<projectid>/cloud-transfer-repo/cloud-transfer-webapp:latest .
```

3. Docker Repo help
```
	gcloud auth configure-docker us-central1-docker.pkg.dev
```

4.  Docker push to Repo

```
	docker push us-central1-docker.pkg.dev/<projectid>/cloud-transfer-repo/cloud-transfer-webapp:latest
```


