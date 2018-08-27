#include <ArduinoJson.h>
#include <PubSubClient.h>
#include <WiFiEspClient.h>
#include <WiFiEsp.h>
#include <WiFiEspUdp.h>
#include <DHT.h>
#include "MillisTimer.h"
#include <LiquidMenu.h>
#include <Wire.h> 
#include <LiquidCrystal_I2C.h>
#include <EEPROM.h>
#include <SoftwareSerial.h>


#define DHTPIN 7
#define DHTTYPE DHT22
#define echoPIN 3 // Echo Pin
#define triggerPIN 4 // Trigger Pin
LiquidCrystal_I2C lcd(0x3F,16,2); 
DHT dht(DHTPIN, DHTTYPE);

#define WIFI_AP "Bornhack-NAT"
#define WIFI_PASSWORD ""
#define TOKEN "smbZqxg7emKgnhNjHNgF"
#define soft Serial1

int info_update_time = 1000;
int menu_update_time = 5000;
int upload_tid = 5000;
int water_level_tid = 60000;
long vande_tid = 10000;
int converted_vande_tid = 10;
long kontrol_tid = 60000;
int converted_kontrol_tid = 1;
int converted_resterende_tid;
int setpunkt_fugt = 50;
int pin_relae_1 = 5;
int pin_relae_2 = 6;
int antal_vandinger = 0;
int pin_knap_1 = 8;
int pin_knap_2 = 9;
int pin_knap_3 = 10;
int pin_knap_4 = 11;
int knap_1 = 0;
int knap_2 = 0;
int knap_3 = 0;
int knap_4 = 0;
char thingsboardServer[] = "emhost.dk";
int returnCM;
WiFiEspClient espClient;
PubSubClient client(espClient);

int status = WL_IDLE_STATUS;
unsigned long lastSend;

MillisTimer update_menu_timer = MillisTimer(menu_update_time);
MillisTimer update_info_timer = MillisTimer(info_update_time);
MillisTimer kontrol_tid_timer = MillisTimer();
MillisTimer vande_tid_timer = MillisTimer();
MillisTimer upload_tid_timer = MillisTimer();
MillisTimer water_level_tid_timer = MillisTimer();





int temperature;
int humidity;


enum FunctionTypes {
  increase = 1,
  decrease = 2,
};


LiquidLine home_1_1(0, 0, "Jord Fugt:"," ", humidity,"%" );
LiquidLine home_1_2(0, 1, "Vandinger:"," ", antal_vandinger);
LiquidScreen home_1(home_1_1,home_1_2);

LiquidLine home_2_1(0, 0, "Temperatur:"," ", temperature," C" );
LiquidLine home_2_2(0, 1, "Luft Fugt :"," ", humidity, " %" );
LiquidScreen home_2(home_2_1,home_2_2);

LiquidLine resterende_tid_1(0, 0 , "Tid til kontrol:" );
LiquidLine resterende_tid_2(0, 1, converted_resterende_tid, " Minutter");
LiquidScreen resterende_tid(resterende_tid_1, resterende_tid_2);

LiquidLine fugt_1(0, 0 , "Setpunkt fugt:" );
LiquidLine fugt_2(0, 1, setpunkt_fugt, " %");
LiquidScreen fugt_menu(fugt_1, fugt_2);

LiquidLine kontrol_1(0, 0 , "Kontroltid:" );
LiquidLine kontrol_2(0, 1, converted_kontrol_tid, " Minutter");
LiquidScreen kontrol_tid_menu(kontrol_1, kontrol_2);

LiquidLine vande_1(0, 0, "Vandetid:");
LiquidLine vande_2(0, 1, converted_vande_tid, " Sekunder");
LiquidScreen vande_tid_menu(vande_1,vande_2);

LiquidMenu menu(lcd);

void setup()
{
 dht.begin();
 Serial.begin(9600);
 InitWiFi();
 client.setServer( thingsboardServer, 1883 );
 lastSend = 0;
 update_menu_timer.expiredHandler(update_menu);
 update_menu_timer.start();

 update_info_timer.expiredHandler(update_info);
 update_info_timer.start();
 
 kontrol_tid_timer.setInterval(kontrol_tid);
 kontrol_tid_timer.expiredHandler(kontrol);
 kontrol_tid_timer.start();
 
 upload_tid_timer.setInterval(upload_tid);
 upload_tid_timer.expiredHandler(wifi_connect);
 upload_tid_timer.start();

 water_level_tid_timer.setInterval(water_level_tid);
 water_level_tid_timer.expiredHandler(water_level);
 water_level_tid_timer.start();

 vande_tid_timer.setInterval(vande_tid);
 vande_tid_timer.expiredHandler(stop_pumpe);
 vande_tid_timer.stop();
 
 pinMode (pin_relae_1, OUTPUT);
 pinMode (pin_relae_2, OUTPUT);
 digitalWrite(pin_relae_1,HIGH);
 digitalWrite(pin_relae_2,HIGH);
 pinMode (pin_knap_1, INPUT);
 digitalWrite(pin_knap_1, HIGH); 
 pinMode (pin_knap_2, INPUT);
 digitalWrite(pin_knap_2, HIGH); 
 pinMode (pin_knap_3, INPUT);
 digitalWrite(pin_knap_3, HIGH); 
 pinMode (pin_knap_4, INPUT);
 digitalWrite(pin_knap_4, HIGH); 
 pinMode(triggerPIN, OUTPUT);          // Set the trigPin as an Output (sr04t or v2.0)
  //pinMode(echoPIN, INPUT);            // Set the echoPin as an Input (sr04t)
 pinMode(echoPIN,INPUT_PULLUP);


 
 lcd.init(); 
 lcd.backlight();
 menu.init();

 kontrol_2.attach_function(1, increase_kontrol_tid);
 kontrol_2.attach_function(2, decrease_kontrol_tid);
 vande_2.attach_function(1, increase_vande_tid);
 vande_2.attach_function(2, decrease_vande_tid);
 fugt_2.attach_function(1, increase_fugt);
 fugt_2.attach_function(2, decrease_fugt);


 menu.add_screen(home_1);
 menu.add_screen(home_2);
 menu.add_screen(resterende_tid);
 menu.add_screen(fugt_menu);
 menu.add_screen(kontrol_tid_menu);
 menu.add_screen(vande_tid_menu);
 


 menu.update();


}  // end setup

void increase_kontrol_tid() {
    kontrol_tid += 60000;
    converted_kontrol_tid = kontrol_tid / 60000;
    delay(100);
}

void decrease_kontrol_tid() {
    kontrol_tid -= 60000;
    converted_kontrol_tid = kontrol_tid / 60000;
    delay(100);
}

void increase_vande_tid() {
    vande_tid += 1000;
    converted_vande_tid = vande_tid / 1000;
    delay(100);
}

void decrease_vande_tid() {
    vande_tid -= 1000;
    converted_vande_tid = vande_tid / 1000;
    delay(100);

}

void increase_fugt() {
    setpunkt_fugt += 1;
    delay(100);
}

void decrease_fugt() {
    setpunkt_fugt -= 1;
    delay(100);

}

void buttonsCheck() {
  knap_1 = digitalRead(pin_knap_1);  
  knap_2 = digitalRead(pin_knap_2);  
  knap_3 = digitalRead(pin_knap_3);  
  knap_4 = digitalRead(pin_knap_4);  



  if (knap_1 == LOW) {         
    menu.switch_focus();
    menu.next_screen();
    delay(100);
    menu.switch_focus();
  } 
  
  if (knap_2 == LOW) {         
    menu.switch_focus();
    menu.previous_screen();
    delay(100);
    menu.switch_focus();
  } 
  if (knap_3 == LOW) {         
    menu.call_function(increase);
    delay(100);
  } 
  if (knap_4 == LOW) {         
    menu.call_function(decrease);
    delay(100);
  } 
}


void kontrol(){
  if(humidity <= setpunkt_fugt){
    water_level();
    start_pumpe();

  }
}


void start_pumpe(){  
    antal_vandinger ++;
    digitalWrite(pin_relae_1,LOW);
    digitalWrite(pin_relae_2,LOW);
    kontrol_tid_timer.stop();
    kontrol_tid_timer.setInterval(kontrol_tid);
    kontrol_tid_timer.reset();
    vande_tid_timer.start();
  }

void stop_pumpe(){  
    digitalWrite(pin_relae_1,HIGH);
    digitalWrite(pin_relae_2,HIGH);
    kontrol_tid_timer.start();
    vande_tid_timer.stop();
    vande_tid_timer.setInterval(vande_tid);
    vande_tid_timer.reset();
  }

void update_info(){
  temperature = dht.readTemperature();
  humidity = dht.readHumidity();
 }

void update_menu(){
  converted_resterende_tid = kontrol_tid_timer.getRemainingTime() / 60000;
  menu.update();
}

void water_level() {
  int distanceCM = 0;                     
  unsigned long durationMS = 0;           
  // Do sounding here
  distanceCM = 0;
  durationMS = 0;
  // Clear the trigger pin
  digitalWrite(triggerPIN, LOW);
  delayMicroseconds(2);
  // Sets the trigger on HIGH state for 10 micro seconds
  digitalWrite(triggerPIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(triggerPIN, LOW);
  // wait for the echo
  durationMS = pulseIn(echoPIN, HIGH);
  // Calculating the distance
  distanceCM = (((int) durationMS * 0.034) / 2);
  // Prints the distance on the Serial Monitor
  Serial.print("Sample: ");
  Serial.println(distanceCM);
}

void getAndSendTemperatureAndHumidityData()
{
  Serial.println("Collecting temperature data.");

  // Reading temperature or humidity takes about 250 milliseconds!
  float h = dht.readHumidity();
  // Read temperature as Celsius (the default)
  float t = dht.readTemperature();

  // Check if any reads failed and exit early (to try again).
  if (isnan(h) || isnan(t)) {
    Serial.println("Failed to read from DHT sensor!");
    return;
  }

  Serial.print("Humidity: ");
  Serial.print(h);
  Serial.print(" %\t");
  Serial.print("Temperature: ");
  Serial.print(t);
  Serial.print(" *C ");


  // Just debug messages
  Serial.print( "Sending temperature and humidity : [" );
  Serial.print( temperature ); Serial.print( "," );
  Serial.print( humidity );
  Serial.print( antal_vandinger );
  Serial.print( "]   -> " );

  // Prepare a JSON payload string
  String payload = "{";
  payload += "\"temperature\":"; payload += temperature; payload += ",";
  payload += "\"humidity\":"; payload += humidity; payload += ",";
  payload += "\"water level\":"; payload += returnCM; payload += ",";
  payload += "\"vandinger\":"; payload += antal_vandinger;
  payload += "}";

  // Send payload
  char attributes[100];
  payload.toCharArray( attributes, 100 );
  client.publish( "v1/devices/me/telemetry", attributes );
  Serial.println( attributes );
}

void InitWiFi()
{
  // initialize serial for ESP module
  soft.begin(9600);
  // initialize ESP module
  WiFi.init(&soft);
  // check for the presence of the shield
  if (WiFi.status() == WL_NO_SHIELD) {
    Serial.println("WiFi shield not present");
    // don't continue
    while (true);
  }

  Serial.println("Connecting to AP ...");
  // attempt to connect to WiFi network
  while ( status != WL_CONNECTED) {
    Serial.print("Attempting to connect to WPA SSID: ");
    Serial.println(WIFI_AP);
    // Connect to WPA/WPA2 network
    status = WiFi.begin(WIFI_AP, WIFI_PASSWORD);
    delay(500);
  }
  Serial.println("Connected to AP");
}

void reconnect() {
  // Loop until we're reconnected
  while (!client.connected()) {
    Serial.print("Connecting to ThingsBoard node ...");
    // Attempt to connect (clientId, username, password)
    if ( client.connect("Arduino Uno Device", TOKEN, NULL) ) {
      Serial.println( "[DONE]" );
    } else {
      Serial.print( "[FAILED] [ rc = " );
      Serial.print( client.state() );
      Serial.println( " : retrying in 5 seconds]" );
      // Wait 5 seconds before retrying
      delay( 5000 );
    }
  }
}

void wifi_connect() {
  status = WiFi.status();
  if ( status != WL_CONNECTED) {
    while ( status != WL_CONNECTED) {
      Serial.print("Attempting to connect to WPA SSID: ");
      Serial.println(WIFI_AP);
      // Connect to WPA/WPA2 network
      status = WiFi.begin(WIFI_AP, WIFI_PASSWORD);
      delay(500);
    }
    Serial.println("Connected to AP");
  }

  if ( !client.connected() ) {
    reconnect();
  }

  if ( millis() - lastSend > 1000 ) { // Update and send only after 1 seconds
    getAndSendTemperatureAndHumidityData();
    lastSend = millis();
  }

  client.loop();
}

void loop(){
  buttonsCheck();
  update_menu_timer.run();
  update_info_timer.run();
  kontrol_tid_timer.run();
  vande_tid_timer.run();
  upload_tid_timer.run();
  water_level_tid_timer.run();

}



