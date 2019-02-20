
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

#define WIFI_AP "StativStakitKasket-2.4GHz"
#define WIFI_PASSWORD "123456789a"
#define TOKEN "smbZqxg7emKgnhNjHNgF"
#define soft Serial1

int info_update_time = 60000;
int menu_update_time = 5000;
long vande_tid = 10000;
int converted_vande_tid = 10;
long kontrol_tid = 60000;
int converted_kontrol_tid = 1;
long converted_resterende_tid;
int setpunkt_fugt = 30;
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
int temperature;
int humidity;
const int analogInPin = A0;  
int moistureValue, jord_fugt;

int status = WL_IDLE_STATUS;
unsigned long lastSend;

MillisTimer update_menu_timer = MillisTimer(menu_update_time);
MillisTimer update_info_timer = MillisTimer(info_update_time);
MillisTimer kontrol_tid_timer = MillisTimer();
MillisTimer vande_tid_timer = MillisTimer();

enum FunctionTypes {
  increase = 1,
  decrease = 2,
};


LiquidLine home_1_1(0, 0, "Jord Fugt:"," ", jord_fugt,"%" );
LiquidLine home_1_2(0, 1, "Vandinger:"," ", antal_vandinger);
LiquidScreen home_1(home_1_1,home_1_2);

LiquidLine home_2_1(0, 0, "Temperatur:"," ", temperature," C" );
LiquidLine home_2_2(0, 1, "Luft Fugt :"," ", humidity, " %" );
LiquidScreen home_2(home_2_1,home_2_2);

LiquidLine resterende_tid_1(0, 0 , "Tid til kontrol:" );
LiquidLine resterende_tid_2(0, 1, converted_resterende_tid, " Sekunder");
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

LiquidLine manual_1(0, 0, "Manual vanding");
LiquidLine manual_2(0, 1, "START + STOP -");
LiquidScreen manual_vanding_menu(manual_1,manual_2);

LiquidMenu menu(lcd);

void setup()
{
 dht.begin();
 Serial.begin(9600);
 lastSend = 0;
 update_menu_timer.expiredHandler(update_menu);
 update_menu_timer.start();

 update_info_timer.expiredHandler(update_info);
 update_info_timer.start();
 
 kontrol_tid_timer.setInterval(kontrol_tid);
 kontrol_tid_timer.expiredHandler(kontrol);
 kontrol_tid_timer.start();
 
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
 manual_1.attach_function(1, manual_vanding_start);
 manual_1.attach_function(2, manual_vanding_start);





 menu.add_screen(home_1);
 menu.add_screen(home_2);
 menu.add_screen(resterende_tid);
 menu.add_screen(fugt_menu);
 menu.add_screen(kontrol_tid_menu);
 menu.add_screen(vande_tid_menu);
 menu.add_screen(manual_vanding_menu);

 menu.update();
 update_menu();
 update_info();

}  // end setup

void manual_vanding_start() {
  if (knap_3 == HIGH)
  {
    digitalWrite(pin_relae_1,HIGH);
    digitalWrite(pin_relae_2,HIGH);
  }
  else if (knap_4 == HIGH)
  {
    digitalWrite(pin_relae_1,LOW);
    digitalWrite(pin_relae_2,LOW);
  }
}



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
  if(jord_fugt <= setpunkt_fugt){
    start_pumpe();

  }
}


void start_pumpe(){  
    Serial.println("Start pumpe");
    Serial.println(vande_tid);
    antal_vandinger ++;
    digitalWrite(pin_relae_1,LOW);
    digitalWrite(pin_relae_2,LOW);
    kontrol_tid_timer.stop();
    kontrol_tid_timer.setInterval(kontrol_tid);
    kontrol_tid_timer.reset();
    vande_tid_timer.start();
  }

void stop_pumpe(){  
    Serial.println("Stop pumpe");
    digitalWrite(pin_relae_1,HIGH);
    digitalWrite(pin_relae_2,HIGH);
    kontrol_tid_timer.start();
    vande_tid_timer.stop();
    vande_tid_timer.setInterval(vande_tid);
    vande_tid_timer.reset();
  }

void update_info(){
  Serial.println("indsamler info");
  temperature = dht.readTemperature();
  humidity = dht.readHumidity();

 }

void update_menu(){
  Serial.println("Opdatere skærm");
  Serial.println(vande_tid);
  Serial.println(kontrol_tid);

  converted_resterende_tid = kontrol_tid_timer.getRemainingTime()/ 1000 ;
  menu.update();
}


void loop(){
  buttonsCheck();
  update_menu_timer.run();
  update_info_timer.run();
  kontrol_tid_timer.run();
  vande_tid_timer.run();

}
