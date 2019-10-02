FROM python:3.7.4-stretch

WORKDIR /usr/src/app
ADD simple-server.py .
ADD requirements.txt .

RUN pip install --no-cache-dir -r requirements.txt

CMD ["python", "simple-server.py", "--disable-ssl"]